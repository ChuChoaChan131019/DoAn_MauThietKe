package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.*;
import com.senvia.doangiuaky.merchant.api.*;
import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutPreparationServiceTest {

    private CurrentUserApi currentUser;
    private CheckoutCartApi cartApi;
    private MerchantApi merchant;
    private CheckoutPreparationService service;

    @BeforeEach
    void init() {
        currentUser = mock(CurrentUserApi.class);
        cartApi = mock(CheckoutCartApi.class);
        merchant = mock(MerchantApi.class);

        service = new CheckoutPreparationService(
                currentUser,
                cartApi,
                merchant
        );

        when(currentUser.requireCurrentUser()).thenReturn(
                new UserSummary(
                        10L,
                        "Người mua",
                        UserRole.USER,
                        AccountStatus.ACTIVE
                )
        );
    }

    @Test
    void oneShopCreatesOneCalculatedOrderWithSnapshottedPricesAndAddress() {

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 2),
                        new CheckoutCartItem(2L, 102L, 1)
                )
        );

        product(
                101, 21, 99,
                "Túi vải", "100000.00", 5, true
        );

        product(
                102, 21, 99,
                "Cốc sứ", "80000.00", 3, true
        );

        shop(21, true);

        PreparedCheckout result = service.prepare(form());

        assertEquals(1, result.shopOrders().size());
        assertEquals(10L, result.buyerId());
        assertEquals(
                21L,
                result.shopOrders().getFirst().shopId()
        );
        assertEquals(99L, result.shopOrders().getFirst().shopOwnerId());

        assertEquals(
                new BigDecimal("280000.00"),
                result.grandTotal()
        );

        assertEquals(
                new BigDecimal("280000.00"),
                result.shopOrders().getFirst().totalAmount()
        );

        var firstLine = result.shopOrders()
                .getFirst()
                .items()
                .getFirst();

        assertEquals(
                "Túi vải",
                firstLine.productNameSnapshot()
        );

        assertEquals(
                new BigDecimal("100000.00"),
                firstLine.unitPrice()
        );

        assertEquals(
                new BigDecimal("200000.00"),
                firstLine.subtotal()
        );

        assertEquals(
                "Nguyễn Văn A",
                result.delivery().receiverName()
        );

        assertEquals(
                "123 Lê Lợi",
                result.delivery().shippingAddress()
        );

        assertNull(result.delivery().note());

        verify(merchant, times(1))
                .findShopOrderCapabilities(21L);
    }

    @Test
    void selectedValidItemCanBePreparedWhileUnselectedInvalidItemRemainsInCart() {
        when(cartApi.getCheckoutSnapshot()).thenReturn(List.of(
                new CheckoutCartItem(1L, 101L, 2),
                new CheckoutCartItem(2L, 102L, 1)));
        product(101, 21, 99, "Túi vải", "100000.00", 5, true);
        shop(21, true);

        PreparedCheckout result = service.prepare(form(), List.of(1L));

        assertEquals(new BigDecimal("200000.00"), result.grandTotal());
        assertEquals(1, result.shopOrders().getFirst().items().size());
        verify(merchant, never()).findProductForCart(102L);
    }

    @Test
    void selectedItemMustBelongToAuthenticatedCart() {
        when(cartApi.getCheckoutSnapshot()).thenReturn(List.of(new CheckoutCartItem(1L, 101L, 2)));

        assertThrows(CheckoutPreparationException.class,
                () -> service.prepare(form(), List.of(999L)));
        assertThrows(CheckoutPreparationException.class,
                () -> service.prepare(form(), List.of(1L, 1L)));
        verifyNoInteractions(merchant);
    }

    @Test
    void multipleShopsHaveSeparateAmountsAndSameAuthenticatedBuyer() {

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 2),
                        new CheckoutCartItem(2L, 102L, 1),
                        new CheckoutCartItem(3L, 103L, 1)
                )
        );

        product(
                101, 21, 99,
                "Túi", "100000.00", 5, true
        );

        product(
                102, 21, 99,
                "Cốc", "80000.00", 5, true
        );

        product(
                103, 22, 98,
                "Đèn", "350000.00", 5, true
        );

        shop(21, true);
        shop(22, true);

        var result = service.prepare(form());

        assertEquals(2, result.shopOrders().size());

        assertEquals(
                21L,
                result.shopOrders().get(0).shopId()
        );

        assertEquals(
                22L,
                result.shopOrders().get(1).shopId()
        );
        assertEquals(99L, result.shopOrders().get(0).shopOwnerId());
        assertEquals(98L, result.shopOrders().get(1).shopOwnerId());

        assertEquals(
                new BigDecimal("280000.00"),
                result.shopOrders().get(0).totalAmount()
        );

        assertEquals(
                new BigDecimal("350000.00"),
                result.shopOrders().get(1).totalAmount()
        );

        assertEquals(
                new BigDecimal("630000.00"),
                result.grandTotal()
        );

        assertTrue(
                result.shopOrders()
                        .stream()
                        .allMatch(o -> o.buyerId().equals(10L))
        );

        assertEquals(
                3,
                result.purchasedCartItems().size()
        );
    }

    @Test
    void emptyCartIsRejected() {

        when(cartApi.getCheckoutSnapshot())
                .thenReturn(List.of());

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );

        verifyNoInteractions(merchant);
    }

    @Test
    void missingProductIsRejected() {

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 1)
                )
        );

        when(merchant.findProductForCart(101L))
                .thenReturn(Optional.empty());

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );
    }

    @Test
    void unavailableOrInsufficientStockProductIsRejected() {

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 3)
                )
        );

        product(
                101, 21, 99,
                "Túi", "100000.00", 2, true
        );

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );

        product(
                101, 21, 99,
                "Túi", "100000.00", 5, false
        );

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );
    }

    @Test
    void shopCannotReceiveOrders() {

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 1)
                )
        );

        product(
                101, 21, 99,
                "Túi", "100000.00", 5, true
        );

        shop(21, false);

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );
    }

    @Test
    void ownShopPurchaseIsForbidden() {

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 1)
                )
        );

        product(
                101, 21, 10,
                "Túi", "100000.00", 5, true
        );

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );

        verify(merchant, never())
                .findShopOrderCapabilities(anyLong());
    }

    @Test
    void inconsistentOwnersForTheSameShopAreRejected() {
        when(cartApi.getCheckoutSnapshot()).thenReturn(List.of(
                new CheckoutCartItem(1L, 101L, 1),
                new CheckoutCartItem(2L, 102L, 1)));
        product(101, 21, 99, "Túi", "100000.00", 5, true);
        product(102, 21, 98, "Cốc", "80000.00", 5, true);
        shop(21, true);

        assertThrows(CheckoutPreparationException.class, () -> service.prepare(form()));
    }

    @Test
    void errorInSecondShopRejectsAllWithoutAnyDatabaseWrite() {

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 1),
                        new CheckoutCartItem(2L, 102L, 1)
                )
        );

        product(
                101, 21, 99,
                "Túi", "100000.00", 5, true
        );

        product(
                102, 22, 98,
                "Đèn", "350000.00", 5, true
        );

        shop(21, true);
        shop(22, false);

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );

        verify(cartApi, never())
                .removePurchasedItems(anyList());
    }

    @Test
    void rejectsDuplicatedCartItemsAndTooManyFractionDigits() {

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 1),
                        new CheckoutCartItem(1L, 101L, 1)
                )
        );

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );

        when(cartApi.getCheckoutSnapshot()).thenReturn(
                List.of(
                        new CheckoutCartItem(1L, 101L, 1)
                )
        );

        product(
                101, 21, 99,
                "Túi", "100.001", 5, true
        );

        shop(21, true);

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );
    }

    @Test
    void inactiveBuyerRejectedBeforeCartLookup() {

        when(currentUser.requireCurrentUser()).thenReturn(
                new UserSummary(
                        10L,
                        "Buyer",
                        UserRole.USER,
                        AccountStatus.LOCKED
                )
        );

        assertThrows(
                CheckoutPreparationException.class,
                () -> service.prepare(form())
        );

        verifyNoInteractions(cartApi);
    }

    private CheckoutPaymentForm form() {

        var f = new CheckoutPaymentForm();

        f.setReceiverName("  Nguyễn Văn A ");
        f.setReceiverPhone(" 0901234567 ");
        f.setShippingAddress(" 123 Lê Lợi ");
        f.setPaymentMethod(PaymentMethod.COD);

        return f;
    }

    private void product(
            long id,
            long shopId,
            long ownerId,
            String name,
            String price,
            int stock,
            boolean active
    ) {

        when(merchant.findProductForCart(id)).thenReturn(
                Optional.of(
                        new CartProductView(
                                id,
                                shopId,
                                ownerId,
                                "Shop " + shopId,
                                name,
                                null,
                                new BigDecimal(price),
                                stock,
                                true,
                                true,
                                true,
                                active
                        )
                )
        );
    }

    private void shop(long id, boolean canReceive) {

        when(merchant.findShopOrderCapabilities(id))
                .thenReturn(Optional.of(new ShopOrderCapabilities(id, canReceive, true)));
    }
}
