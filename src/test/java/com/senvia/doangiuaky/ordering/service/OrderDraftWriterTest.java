package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDraftWriterTest {

    @Test
    void rejectsIncreasedPriceBeforeAnyOrderSideWriteAndReturnsCurrentTotal() {
        var fixture = fixture("100.00");
        when(fixture.merchant().findProductForCart(101L))
                .thenReturn(Optional.of(product("120.00", 5, true)));

        CheckoutPriceChangedException exception = assertThrows(
                CheckoutPriceChangedException.class,
                () -> fixture.writer().save(fixture.checkout(), paymentInitializer()));

        assertEquals(new BigDecimal("240.00"), exception.getCurrentGrandTotal());
        verifyNoInteractions(fixture.orders(), fixture.items(), fixture.payments());
    }

    @Test
    void rejectsDecreasedPriceBeforeAnyOrderSideWrite() {
        var fixture = fixture("100.00");
        when(fixture.merchant().findProductForCart(101L))
                .thenReturn(Optional.of(product("80.00", 5, true)));

        assertThrows(CheckoutPriceChangedException.class,
                () -> fixture.writer().save(fixture.checkout(), paymentInitializer()));

        verifyNoInteractions(fixture.orders(), fixture.items(), fixture.payments());
    }

    @Test
    void equalPricesWithDifferentScaleAreAccepted() {
        var fixture = fixture("100.0");
        when(fixture.merchant().findProductForCart(101L))
                .thenReturn(Optional.of(product("100.00", 5, true)));
        var savedOrder = mock(com.senvia.doangiuaky.ordering.entity.Order.class);
        when(savedOrder.getId()).thenReturn(77L);
        when(fixture.orders().save(any())).thenReturn(savedOrder);

        assertEquals(List.of(77L), fixture.writer().save(fixture.checkout(), paymentInitializer()));

        verify(fixture.orders()).save(any());
        verify(fixture.items()).save(any());
        verify(fixture.payments()).save(any());
    }

    @Test
    void rejectsUnavailableOrInsufficientProductBeforeAnyOrderSideWrite() {
        var unavailable = fixture("100.00");
        when(unavailable.merchant().findProductForCart(101L))
                .thenReturn(Optional.of(product("100.00", 5, false)));

        assertThrows(CheckoutPreparationException.class,
                () -> unavailable.writer().save(unavailable.checkout(), paymentInitializer()));
        verifyNoInteractions(unavailable.orders(), unavailable.items(), unavailable.payments());

        var insufficient = fixture("100.00");
        when(insufficient.merchant().findProductForCart(101L))
                .thenReturn(Optional.of(product("100.00", 1, true)));

        assertThrows(CheckoutPreparationException.class,
                () -> insufficient.writer().save(insufficient.checkout(), paymentInitializer()));
        verifyNoInteractions(insufficient.orders(), insufficient.items(), insufficient.payments());
    }

    @Test
    void shouldRejectDuplicateShopIdBeforeSaving() {

        // 1. Gia lap repository
        OrderRepository orders = mock(OrderRepository.class);
        OrderItemRepository items = mock(OrderItemRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        MerchantApi merchantApi = mock(MerchantApi.class);

        OrderDraftWriter writer = new OrderDraftWriter(
                orders,
                items,
                payments,
                merchantApi
        );

        // 2. Tao nhom dau tien
        var firstShop = new PreparedCheckout.ShopOrder(
                10L,
                21L,
                List.of(
                        new PreparedCheckout.Line(
                                101L,
                                "Túi vải",
                                new BigDecimal("100000.00"),
                                1,
                                new BigDecimal("100000.00")
                        )
                ),
                new BigDecimal("100000.00")
        );

        // 3. Tao nhom thu hai co cung shopId = 21
        var secondShop = new PreparedCheckout.ShopOrder(
                10L,
                21L,
                List.of(
                        new PreparedCheckout.Line(
                                102L,
                                "Cốc sứ",
                                new BigDecimal("80000.00"),
                                1,
                                new BigDecimal("80000.00")
                        )
                ),
                new BigDecimal("80000.00")
        );

        // 4. Tao checkout co hai nhom trung shop
        PreparedCheckout checkout = new PreparedCheckout(
                10L,
                new PreparedCheckout.DeliverySnapshot(
                        "Nguyễn Văn A",
                        "0901234567",
                        "Đà Lạt",
                        null
                ),
                PaymentMethod.COD,
                List.of(firstShop, secondShop),
                new BigDecimal("180000.00"),
                List.of()
        );

        // 5. Kiem tra exception
        CheckoutPreparationException exception = assertThrows(
                CheckoutPreparationException.class,
                () -> writer.save(
                        checkout,
                        (method, code, amount) -> {
                            fail("Không được khởi tạo payment");
                            return null;
                        }
                )
        );

        // 6. Xac nhan dung loi shop trung
        assertTrue(
                exception.getMessage().contains(
                        "xuất hiện nhiều lần"
                )
        );

        // 7. Khong repository nao duoc ghi du lieu
        verifyNoInteractions(
                orders,
                items,
                payments,
                merchantApi
        );
    }

    private static Fixture fixture(String quotedPrice) {
        OrderRepository orders = mock(OrderRepository.class);
        OrderItemRepository items = mock(OrderItemRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        MerchantApi merchant = mock(MerchantApi.class);
        BigDecimal price = new BigDecimal(quotedPrice);
        PreparedCheckout checkout = new PreparedCheckout(
                10L,
                new PreparedCheckout.DeliverySnapshot("Nguyễn Văn A", "0901234567", "Đà Lạt", null),
                PaymentMethod.COD,
                List.of(new PreparedCheckout.ShopOrder(
                        10L,
                        21L,
                        List.of(new PreparedCheckout.Line(
                                101L, "Túi vải", price, 2, price.multiply(BigDecimal.valueOf(2)))),
                        price.multiply(BigDecimal.valueOf(2)))),
                price.multiply(BigDecimal.valueOf(2)),
                List.of());
        return new Fixture(new OrderDraftWriter(orders, items, payments, merchant), checkout,
                orders, items, payments, merchant);
    }

    private static CartProductView product(String price, int stock, boolean active) {
        return new CartProductView(101L, 21L, 99L, "Shop 21", "Túi vải", null,
                new BigDecimal(price), stock, true, true, true, active);
    }

    private static OrderDraftWriter.PaymentSeedProvider paymentInitializer() {
        return (method, code, amount) ->
                new OrderDraftWriter.PaymentSeed(
                        com.senvia.doangiuaky.ordering.entity.PaymentStatus.COD_PENDING, null);
    }

    private record Fixture(
            OrderDraftWriter writer,
            PreparedCheckout checkout,
            OrderRepository orders,
            OrderItemRepository items,
            PaymentRepository payments,
            MerchantApi merchant) {
    }
}
