package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDraftWriterTest {

    @Test
    void shouldRejectDuplicateShopIdBeforeSaving() {

        // 1. Gia lap repository
        OrderRepository orders = mock(OrderRepository.class);
        OrderItemRepository items = mock(OrderItemRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);

        OrderDraftWriter writer = new OrderDraftWriter(
                orders,
                items,
                payments
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
                payments
        );
    }
}