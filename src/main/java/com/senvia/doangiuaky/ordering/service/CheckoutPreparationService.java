package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;

import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.ShopOrderCapabilities;

import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;

import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class CheckoutPreparationService {

    private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{9,15}");
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("999999999999.99");
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("9999999999.99");

    private final CurrentUserApi currentUserApi;
    private final CheckoutCartApi checkoutCartApi;
    private final MerchantApi merchantApi;

    public CheckoutPreparationService(CurrentUserApi currentUserApi, CheckoutCartApi checkoutCartApi, MerchantApi merchantApi) {
        this.currentUserApi = currentUserApi;
        this.checkoutCartApi = checkoutCartApi;
        this.merchantApi = merchantApi;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PreparedCheckout prepare(CheckoutPaymentForm form) {
        return prepare(form, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PreparedCheckout prepare(CheckoutPaymentForm form, List<Long> selectedItemIds) {
        // 1. Kiem tra form thanh toan
        if (form == null || form.getPaymentMethod() == null) {
            throw new CheckoutPreparationException("Thiếu dữ liệu thanh toán.");
        }

        // 2. Chup lai thong tin nhan hang
        var delivery = new PreparedCheckout.DeliverySnapshot(
                required(
                        form.getReceiverName(),
                        100,
                        "Tên người nhận"
                ),
                required(
                        form.getReceiverPhone(),
                        20,
                        "Số điện thoại"
                ),
                required(
                        form.getShippingAddress(),
                        2000,
                        "Địa chỉ nhận"
                ),
                optional(form.getNote(), 2000)
        );

        if (!PHONE.matcher(delivery.receiverPhone()).matches()) {
            throw new CheckoutPreparationException("Số điện thoại không hợp lệ.");
        }

        // 3. Lay buyer tu tai khoan dang nhap
        UserSummary buyer = currentUserApi.requireCurrentUser();

        if (buyer == null
                || buyer.userId() == null
                || buyer.userId() <= 0
                || buyer.role() != UserRole.USER
                || buyer.accountStatus() != AccountStatus.ACTIVE) {

            throw new CheckoutPreparationException("Tài khoản không được phép mua hàng.");
        }

        // 4. Lay gio hang tu Shopping API
        List<CheckoutCartItem> cart = checkoutCartApi.getCheckoutSnapshot();

        if (cart == null || cart.isEmpty()) {
            throw new CheckoutPreparationException("Giỏ hàng đang trống.");
        }

        if (selectedItemIds != null) {
            if (selectedItemIds.isEmpty()) {
                throw new CheckoutPreparationException("Vui lòng chọn ít nhất một sản phẩm để thanh toán.");
            }
            Set<Long> selected = new HashSet<>();
            for (Long selectedId : selectedItemIds) {
                if (selectedId == null || selectedId <= 0 || !selected.add(selectedId)) {
                    throw new CheckoutPreparationException("Lựa chọn sản phẩm không hợp lệ hoặc bị trùng.");
                }
            }
            cart = cart.stream()
                    .filter(item -> item != null && selected.contains(item.cartItemId()))
                    .toList();
            if (cart.size() != selected.size()) {
                throw new CheckoutPreparationException("Sản phẩm được chọn không thuộc giỏ hàng hiện tại.");
            }
        }

        // 5. Nhom san pham theo shopId
        Map<Long, List<PreparedCheckout.Line>> grouped = new LinkedHashMap<>();
        Map<Long, String> shopNames = new HashMap<>();

        Set<Long> seenCartItemIds = new HashSet<>();
        Set<Long> seenProductIds = new HashSet<>();
        Set<Long> validatedShops = new HashSet<>();

        for (CheckoutCartItem item : cart) {
            // 6. Kiem tra item va so luong
            if (item == null
                    || invalidId(item.cartItemId())
                    || invalidId(item.productId())
                    || item.quantity() <= 0
                    || !seenCartItemIds.add(item.cartItemId())
                    || !seenProductIds.add(item.productId())) {

                throw new CheckoutPreparationException("Giỏ hàng chứa dòng sản phẩm không hợp lệ hoặc bị trùng.");
            }

            // 7. Doc lai san pham tu Merchant
            CartProductView product = merchantApi
                    .findProductForCart(item.productId())
                    .orElseThrow(() -> new CheckoutPreparationException("Sản phẩm không tồn tại: " + item.productId())
                    );

            // 8. Kiem tra san pham co the ban
            if (!item.productId().equals(product.productId())
                    || invalidId(product.shopId())
                    || invalidId(product.shopOwnerId())
                    || !product.isSaleable()
                    || product.stockQuantity() < item.quantity()) {

                throw new CheckoutPreparationException("Sản phẩm đã ngừng bán hoặc không đủ hàng: " + item.productId());
            }

            // 9. Buyer khong duoc mua hang shop minh
            if (buyer.userId().equals(product.shopOwnerId())) {
                throw new CheckoutPreparationException("Không thể mua sản phẩm của gian hàng chính mình.");
            }

            // 10. Kiem tra shop duoc nhan don
            if (validatedShops.add(product.shopId())) {
                ShopOrderCapabilities shop = merchantApi
                        .findShopOrderCapabilities(product.shopId())
                        .orElseThrow(() -> new CheckoutPreparationException("Gian hàng không tồn tại.")
                        );

                if (!product.shopId().equals(shop.shopId())
                        || !shop.canReceiveNewOrders()) {

                    throw new CheckoutPreparationException("Gian hàng không được nhận đơn mới.");
                }
            }

            // 11. Kiem tra ten san pham
            if (product.productName() == null
                    || product.productName().isBlank()
                    || product.productName().length() > 200) {

                throw new CheckoutPreparationException("Tên sản phẩm không hợp lệ.");
            }

            // 12. Lay gia hien tai tu Merchant
            BigDecimal price = money(
                    product.currentPrice(),
                    MAX_UNIT_PRICE,
                    "Giá sản phẩm"
            );

            // 13. Tinh thanh tien
            BigDecimal lineTotal = money(
                    price.multiply(BigDecimal.valueOf(item.quantity())),
                    MAX_ORDER_AMOUNT,
                    "Tổng dòng hàng"
            );

            String shopName = product.shopName();
            shopNames.putIfAbsent(product.shopId(), shopName == null || shopName.isBlank()
                    ? "Gian hàng #" + product.shopId() : shopName.strip());

            // 14. Them san pham vao nhom shop
            grouped.computeIfAbsent(
                    product.shopId(),
                    ignored -> new ArrayList<>()
            ).add(
                    new PreparedCheckout.Line(product.productId(), product.productName(), price, item.quantity(), lineTotal)
            );
        }

        // 15. Tao danh sach don hang theo shop
        List<PreparedCheckout.ShopOrder> shopOrders = new ArrayList<>();

        BigDecimal grandTotal = new BigDecimal("0.00");

        for (var group : grouped.entrySet()) {
            BigDecimal sum = group.getValue()
                    .stream()
                    .map(PreparedCheckout.Line::subtotal)
                    .reduce(new BigDecimal("0.00"), BigDecimal::add);

            sum = money(
                    sum,
                    MAX_ORDER_AMOUNT,
                    "Tổng tiền đơn hàng"
            );

            shopOrders.add(
                    new PreparedCheckout.ShopOrder(
                            buyer.userId(),
                            group.getKey(),
                            shopNames.get(group.getKey()),
                            group.getValue(),
                            sum
                    )
            );

            grandTotal = grandTotal.add(sum);
        }

        // 16. Tra ve du lieu da chuan bi
        return new PreparedCheckout(
                buyer.userId(),
                delivery,
                form.getPaymentMethod(),
                shopOrders,
                grandTotal,
                cart
        );
    }

    private static boolean invalidId(Long id) {
        return id == null || id <= 0;
    }

    private static BigDecimal money(BigDecimal input, BigDecimal max, String label) {
        if (input == null
                || input.signum() <= 0
                || input.compareTo(max) > 0) {

            throw new CheckoutPreparationException(label + " không hợp lệ.");
        }

        try {
            return input.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new CheckoutPreparationException(label + " vượt quá hai chữ số thập phân.", exception);
        }
    }

    private static String required(String value, int max, String label) {
        if (value == null
                || value.isBlank()
                || value.strip().length() > max) {

            throw new CheckoutPreparationException(label + " không hợp lệ.");
        }

        return value.strip();
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }

        if (value.strip().length() > max) {
            throw new CheckoutPreparationException("Ghi chú quá dài.");
        }

        return value.strip();
    }
}
