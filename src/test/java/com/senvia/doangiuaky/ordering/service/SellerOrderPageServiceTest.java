
package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.ordering.dto.PaymentDetails;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SellerOrderPageServiceTest {

    private final CurrentUserApi currentUser =
            mock(CurrentUserApi.class);

    private final MerchantApi merchant =
            mock(MerchantApi.class);

    private final OrderRepository orders =
            mock(OrderRepository.class);

    private final OrderItemRepository items =
            mock(OrderItemRepository.class);

    private final PaymentQueryService payments =
            mock(PaymentQueryService.class);

    private final SellerOrderReadService service =
            new SellerOrderReadService(
                    currentUser,
                    merchant,
                    orders,
                    items,
                    payments
            );

    @BeforeEach
    void setUp() {
        when(currentUser.requireCurrentUser())
                .thenReturn(new UserSummary(
                        7L,
                        "Chủ shop A",
                        UserRole.USER,
                        AccountStatus.ACTIVE
                ));
    }

    @Test
    void onlyOwnShopOrdersAppearInPagedList() {
        // Database có đơn thuộc hai shop.
        when(orders.findDistinctShopIds())
                .thenReturn(List.of(11L, 12L));

        when(merchant.findShopSummary(11L))
                .thenReturn(Optional.of(shop(11L, 7L, true, true)));

        when(merchant.findShopSummary(12L))
                .thenReturn(Optional.of(shop(12L, 8L, true, true)));

        Order ownOrder = createOrder(11L);

        when(orders.findByShopIdIn(
                eq(List.of(11L)),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(ownOrder),
                PageRequest.of(0, 10),
                1
        ));

        when(orders.countByShopIdInAndStatus(
                List.of(11L),
                OrderStatus.PENDING
        )).thenReturn(1L);

        mockOrderDetails();

        var result = service.pageOwnOrders(null, 1);

        assertEquals(1, result.orders().size());
        assertEquals("ORD-1", result.orders().getFirst().id());
        assertEquals(1, result.currentPage());
        assertEquals(1, result.totalPages());
        assertEquals(1, result.pendingCount());

        // Không bao giờ gọi truy vấn Page với shop B.
        verify(orders).findByShopIdIn(
                eq(List.of(11L)),
                argThat(pageable ->
                        pageable.getPageNumber() == 0
                        && pageable.getPageSize() == 10
                        && pageable.getSort()
                                .getOrderFor("id")
                                .isDescending()
                )
        );

        verify(merchant).findShopSummary(12L);

        verify(payments).getByOrderId(21L);
        verifyNoMoreInteractions(payments);
    }

    @Test
    void statusFilterUsesDatabaseQuery() {
        when(orders.findDistinctShopIds())
                .thenReturn(List.of(11L));

        when(merchant.findShopSummary(11L))
                .thenReturn(Optional.of(shop(11L, 7L, true, true)));

        when(orders.findByShopIdInAndStatus(
                eq(List.of(11L)),
                eq(OrderStatus.COMPLETED),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10),
                0
        ));

        var result = service.pageOwnOrders("COMPLETED", 1);

        assertTrue(result.orders().isEmpty());

        verify(orders).findByShopIdInAndStatus(
                eq(List.of(11L)),
                eq(OrderStatus.COMPLETED),
                any(Pageable.class)
        );

        verify(orders, never()).findByShopIdIn(
                anyCollection(),
                any(Pageable.class)
        );

        verifyNoInteractions(items, payments);
    }

    @Test
    void lockedShopCanStillViewExistingOrders() {
        when(orders.findDistinctShopIds())
                .thenReturn(List.of(11L));

        // Shop không còn APPROVED, nhưng owner vẫn ACTIVE.
        when(merchant.findShopSummary(11L))
                .thenReturn(Optional.of(shop(
                        11L,
                        7L,
                        true,
                        false
                )));

        when(orders.findByShopIdIn(
                eq(List.of(11L)),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(createOrder(11L)),
                PageRequest.of(0, 10),
                1
        ));

        mockOrderDetails();

        var result = service.pageOwnOrders(null, 1);

        assertEquals(1, result.orders().size());
        assertEquals("ORD-1", result.orders().getFirst().id());
    }

    @Test
    void userWithNoOwnedOrdersSeesEmptyList() {
        when(orders.findDistinctShopIds())
                .thenReturn(List.of(12L));

        when(merchant.findShopSummary(12L))
                .thenReturn(Optional.of(shop(12L, 8L, true, true)));

        var result = service.pageOwnOrders(null, 1);

        assertTrue(result.orders().isEmpty());
        assertEquals(0, result.totalElements());

        verifyNoInteractions(items, payments);
    }

    @Test
    void invalidStatusIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.pageOwnOrders("INVALID", 1)
        );

        assertEquals(400, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void invalidPageIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.pageOwnOrders(null, 0)
        );

        assertEquals(400, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void inactiveSellerCannotReadOrders() {
        when(currentUser.requireCurrentUser())
                .thenReturn(new UserSummary(
                        7L,
                        "Chủ shop A",
                        UserRole.USER,
                        AccountStatus.LOCKED
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.pageOwnOrders(null, 1)
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void adminCannotReadSellerOrdersThroughService() {
        when(currentUser.requireCurrentUser())
                .thenReturn(new UserSummary(
                        1L,
                        "Admin",
                        UserRole.ADMIN,
                        AccountStatus.ACTIVE
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.pageOwnOrders(null, 1)
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    private void mockOrderDetails() {
        when(items.findAllByOrderIdOrderByIdAsc(21L))
                .thenReturn(List.of());

        when(payments.getByOrderId(21L))
                .thenReturn(new PaymentDetails(
                        21L,
                        PaymentMethod.COD,
                        PaymentStatus.COD_PENDING,
                        new BigDecimal("120000.00"),
                        null,
                        null,
                        null
                ));
    }

    private static Order createOrder(Long shopId) {
        Order order = new Order(
                "ORD-1",
                4L,
                shopId,
                "Người nhận",
                "0900000000",
                "Địa chỉ",
                null,
                new BigDecimal("120000.00")
        );

        ReflectionTestUtils.setField(order, "id", 21L);

        ReflectionTestUtils.setField(
                order,
                "createdAt",
                Instant.parse("2026-10-16T00:00:00Z")
        );

        return order;
    }

    private static MerchantShopSummary shop(
            Long shopId,
            Long ownerId,
            boolean ownerActive,
            boolean shopApproved
    ) {
        return new MerchantShopSummary(
                shopId,
                ownerId,
                "Shop",
                ownerActive,
                shopApproved
        );
    }
}