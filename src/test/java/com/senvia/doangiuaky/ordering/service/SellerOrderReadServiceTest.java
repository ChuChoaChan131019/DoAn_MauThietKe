package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.ordering.dto.PaymentDetails;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerOrderReadServiceTest {
    private final CurrentUserApi currentUser = mock(CurrentUserApi.class);
    private final MerchantApi merchant = mock(MerchantApi.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final PaymentQueryService payments = mock(PaymentQueryService.class);
    private final SellerOrderReadService service =
            new SellerOrderReadService(currentUser, merchant, orders, items, payments);

    @BeforeEach
    void signedInOwner() {
        when(currentUser.requireCurrentUser())
                .thenReturn(new UserSummary(7L, "Chủ shop", UserRole.USER, AccountStatus.ACTIVE));
    }

    @Test
    void ownerReadsCurrentPaymentForOwnOrder() {
        Order order = order(11L);
        when(orders.findByOrderCode("ORD-1")).thenReturn(Optional.of(order));
        when(merchant.findShopSummary(11L)).thenReturn(Optional.of(shop(11L, 7L)));
        when(items.findAllByOrderIdOrderByIdAsc(21L)).thenReturn(List.of());
        when(payments.getByOrderId(21L)).thenReturn(payment(PaymentStatus.PAID));

        var result = service.getOwnOrder("ORD-1");

        assertEquals(PaymentMethod.BANK_TRANSFER, result.payment().method());
        assertEquals(PaymentStatus.PAID, result.payment().status());
        verify(payments).getByOrderId(21L);
    }

    @Test
    void foreignShopOrderIsRejectedBeforePaymentIsRead() {
        when(orders.findByOrderCode("ORD-1")).thenReturn(Optional.of(order(11L)));
        when(merchant.findShopSummary(11L)).thenReturn(Optional.of(shop(11L, 8L)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getOwnOrder("ORD-1"));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(items, payments);
    }

    @Test
    void missingOrderIsNotReplacedWithDemoData() {
        when(orders.findByOrderCode("MISSING")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getOwnOrder("MISSING"));

        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(items, payments);
    }

    @Test
    void listFiltersOtherShopsBeforeReadingPayments() {
        Order own = order(11L);
        when(orders.findDistinctShopIds()).thenReturn(List.of(11L, 12L));
        when(merchant.findShopSummary(11L)).thenReturn(Optional.of(shop(11L, 7L)));
        when(merchant.findShopSummary(12L)).thenReturn(Optional.of(shop(12L, 8L)));
        when(orders.findAllByShopIdInOrderByCreatedAtDesc(List.of(11L)))
                .thenReturn(List.of(own));
        when(items.findAllByOrderIdOrderByIdAsc(21L)).thenReturn(List.of());
        when(payments.getByOrderId(21L)).thenReturn(payment(PaymentStatus.PENDING));

        var result = service.listOwnOrders(null);

        assertEquals(1, result.size());
        assertEquals("ORD-1", result.getFirst().id());
        verify(orders).findAllByShopIdInOrderByCreatedAtDesc(List.of(11L));
        verify(payments).getByOrderId(21L);
        verifyNoMoreInteractions(payments);
    }

    @Test
    void inactiveAccountCannotReadOrderData() {
        when(currentUser.requireCurrentUser())
                .thenReturn(new UserSummary(7L, "Chủ shop", UserRole.USER, AccountStatus.LOCKED));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.listOwnOrders(null));

        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(orders, items, payments);
    }

    private static Order order(Long shopId) {
        Order order = new Order("ORD-1", 4L, shopId, "Người nhận", "0900000000",
                "Địa chỉ", null, new BigDecimal("120000.00"));
        ReflectionTestUtils.setField(order, "id", shopId == 11L ? 21L : 22L);
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-10-16T00:00:00Z"));
        return order;
    }

    private static MerchantShopSummary shop(Long shopId, Long ownerId) {
        return new MerchantShopSummary(shopId, ownerId, "Shop", true, true);
    }

    private static PaymentDetails payment(PaymentStatus status) {
        return new PaymentDetails(21L, PaymentMethod.BANK_TRANSFER, status,
                new BigDecimal("120000.00"), "REF-1", null, null);
    }
}
