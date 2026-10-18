
package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.*;
import com.senvia.doangiuaky.merchant.api.*;
import com.senvia.doangiuaky.ordering.api.*;
import com.senvia.doangiuaky.ordering.entity.*;
import com.senvia.doangiuaky.ordering.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderingContractServiceTest {

    private final CurrentUserApi currentUser =
            mock(CurrentUserApi.class);

    private final MerchantApi merchant =
            mock(MerchantApi.class);

    private final OrderRepository orders =
            mock(OrderRepository.class);

    private final OrderItemRepository items =
            mock(OrderItemRepository.class);

    private final PaymentRepository payments =
            mock(PaymentRepository.class);

    private final OrderHistoryApi histories =
            mock(OrderHistoryApi.class);

    private final OrderingContractService service =
            new OrderingContractService(
                    currentUser,
                    merchant,
                    orders,
                    items,
                    payments,
                    histories
            );

    @BeforeEach
    void buyer() {
        when(currentUser.requireCurrentUser())
                .thenReturn(new UserSummary(
                        7L,
                        "Buyer",
                        UserRole.USER,
                        AccountStatus.ACTIVE
                ));
    }

    @Test
    void buyerOnlyListsOwnOrders() {
        when(orders.findByBuyerId(
                eq(7L),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(order(7L, 11L, OrderStatus.PENDING))
        ));

        var result = service.listOwnOrders(null, 1, 10);

        assertEquals(1, result.orders().size());
        assertEquals(
                Long.valueOf(7L),
                result.orders().getFirst().buyerId()
        );

        verify(orders).findByBuyerId(
                eq(7L),
                any(Pageable.class)
        );
    }

    @Test
    void buyerCannotReadForeignOrderOrPayment() {
        when(orders.findByOrderCode("ORD-1"))
                .thenReturn(Optional.of(
                        order(8L, 11L, OrderStatus.PENDING)
                ));

        assertStatus(
                404,
                () -> service.getOwnOrder("ORD-1")
        );

        verifyNoInteractions(payments, items, histories);
    }

    @Test
    void adminCanFilterAndReadFullOrder() {
        admin();

        Order order = order(
                7L,
                11L,
                OrderStatus.COMPLETED
        );

        when(orders.findAll(
                any(Specification.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(order)));

        when(orders.findByOrderCode("ORD-1"))
                .thenReturn(Optional.of(order));

        when(payments.findByOrderId(33L))
                .thenReturn(Optional.of(new Payment(
                        33L,
                        PaymentMethod.COD,
                        new BigDecimal("250000"),
                        PaymentStatus.PAID,
                        null
                )));

        when(items.findAllByOrderIdOrderByIdAsc(33L))
                .thenReturn(List.of());

        when(histories.forCurrentAdmin("ORD-1"))
                .thenReturn(List.of());

        assertEquals(
                1,
                service.listOrders(
                        "completed",
                        7L,
                        11L,
                        "ORD",
                        1,
                        10
                ).orders().size()
        );

        assertEquals(
                "PAID",
                service.getOrder("ORD-1").payment().status()
        );
    }

    @Test
    void buyerOnlyReviewsCompletedItemWithMatchingProduct() {
        Order order = order(
                7L,
                11L,
                OrderStatus.COMPLETED
        );

        OrderItem item = new OrderItem(
                33L,
                65L,
                "Áo",
                BigDecimal.TEN,
                1,
                BigDecimal.TEN
        );

        ReflectionTestUtils.setField(item, "id", 77L);

        when(items.findById(77L))
                .thenReturn(Optional.of(item));

        when(orders.findById(33L))
                .thenReturn(Optional.of(order));

        assertEquals(
                Long.valueOf(11L),
                service.requireReviewableItem(77L, 65L).shopId()
        );

        assertStatus(
                404,
                () -> service.requireReviewableItem(77L, 99L)
        );

        order.changeStatus(OrderStatus.SHIPPING);

        assertStatus(
                409,
                () -> service.requireReviewableItem(77L, 65L)
        );
    }

    @Test
    void shopStatisticsExcludeOtherShopsAndUncompletedOrdersFromRevenue() {
        when(merchant.findShopSummary(11L))
                .thenReturn(Optional.of(
                        new MerchantShopSummary(
                                11L,
                                7L,
                                "Shop",
                                true,
                                false
                        )
                ));

        when(orders.countOrdersByShopAndStatus(11L))
                .thenReturn(List.of(
                        new Object[]{OrderStatus.PENDING, 2L},
                        new Object[]{OrderStatus.COMPLETED, 3L},
                        new Object[]{OrderStatus.CANCELLED, 1L}
                ));

        when(orders.sumAmountByShopAndStatus(
                11L,
                OrderStatus.COMPLETED
        )).thenReturn(new BigDecimal("550000.00"));

        var result = service.forOwnShop(11L);

        assertEquals(6, result.totalOrders());

        assertEquals(
                Long.valueOf(3L),
                result.ordersByStatus().get("COMPLETED")
        );

        assertEquals(
                new BigDecimal("550000.00"),
                result.completedRevenue()
        );

        verify(orders).sumAmountByShopAndStatus(
                11L,
                OrderStatus.COMPLETED
        );
    }

    @Test
    void foreignShopStatisticsAreHidden() {
        when(merchant.findShopSummary(11L))
                .thenReturn(Optional.of(
                        new MerchantShopSummary(
                                11L,
                                99L,
                                "Other",
                                true,
                                true
                        )
                ));

        assertStatus(
                404,
                () -> service.forOwnShop(11L)
        );

        verifyNoInteractions(orders);
    }

    @Test
    void inactiveAccountCannotQuery() {
        when(currentUser.requireCurrentUser())
                .thenReturn(new UserSummary(
                        7L,
                        "Buyer",
                        UserRole.USER,
                        AccountStatus.LOCKED
                ));

        assertStatus(
                403,
                () -> service.listOwnOrders(null, 1, 10)
        );

        verifyNoInteractions(orders);
    }

    private void admin() {
        when(currentUser.requireCurrentUser())
                .thenReturn(new UserSummary(
                        1L,
                        "Admin",
                        UserRole.ADMIN,
                        AccountStatus.ACTIVE
                ));
    }

    private static Order order(
            Long buyerId,
            Long shopId,
            OrderStatus status
    ) {
        Order order = new Order(
                "ORD-1",
                buyerId,
                shopId,
                "Người nhận",
                "0900000000",
                "Địa chỉ",
                null,
                new BigDecimal("250000")
        );

        ReflectionTestUtils.setField(order, "id", 33L);
        order.changeStatus(status);

        return order;
    }

    private static void assertStatus(
            int status,
            Runnable action
    ) {
        ResponseStatusException ex =
                assertThrows(
                        ResponseStatusException.class,
                        action::run
                );

        assertEquals(status, ex.getStatusCode().value());
    }
}
