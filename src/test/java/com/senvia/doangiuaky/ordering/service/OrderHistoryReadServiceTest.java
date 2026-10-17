
package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;

import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;

import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.OrderStatusHistory;

import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.OrderStatusHistoryRepository;

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

class OrderHistoryReadServiceTest {

    private final CurrentUserApi currentUserApi =
            mock(CurrentUserApi.class);

    private final MerchantApi merchantApi =
            mock(MerchantApi.class);

    private final OrderRepository orders =
            mock(OrderRepository.class);

    private final OrderStatusHistoryRepository histories =
            mock(OrderStatusHistoryRepository.class);

    private final OrderHistoryReadService service =
            new OrderHistoryReadService(
                    currentUserApi,
                    merchantApi,
                    orders,
                    histories
            );

    private static final String ORDER_CODE = "ORD-07";

    private static final Long BUYER_ID = 10L;
    private static final Long SELLER_ID = 11L;
    private static final Long SHOP_ID = 20L;
    private static final Long ORDER_ID = 30L;

    @BeforeEach
    void setup() {
        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        SELLER_ID,
                        UserRole.USER,
                        AccountStatus.ACTIVE
                ));
    }

    @Test
    void sellerCanReadOwnOrderHistory() {

        prepareOrder();
        prepareOwnShop();
        prepareHistory();

        var result = service.forCurrentSeller(ORDER_CODE);

        assertEquals(3, result.size());

        // Mốc đầu tiên là PENDING.
        assertNull(result.get(0).oldStatus());
        assertEquals("PENDING", result.get(0).newStatus());
        assertEquals("Đơn hàng được tạo",
                result.get(0).statusLabel());

        // Mốc thứ hai.
        assertEquals("PENDING", result.get(1).oldStatus());
        assertEquals("CONFIRMED", result.get(1).newStatus());

        // Mốc cuối là hủy.
        assertEquals("PREPARING", result.get(2).oldStatus());
        assertEquals("CANCELLED", result.get(2).newStatus());

        assertEquals(
                "Hủy đơn. Lý do: Sản phẩm bị lỗi",
                result.get(2).note()
        );

        assertEquals(
                SELLER_ID,
                result.get(2).changedBy()
        );

        verify(histories)
                .findAllByOrderIdOrderByChangedAtAscIdAsc(ORDER_ID);
    }

    @Test
    void historyTimeIsFormattedInVietnamTimezone() {

        prepareOrder();
        prepareOwnShop();

        var entry = history(
                1L,
                null,
                OrderStatus.PENDING,
                BUYER_ID,
                "Đơn hàng được tạo",
                "2026-10-17T09:00:00Z"
        );

        when(histories.findAllByOrderIdOrderByChangedAtAscIdAsc(ORDER_ID))
                .thenReturn(List.of(entry));

        var result = service.forCurrentSeller(ORDER_CODE);

        assertEquals(
                "17/10/2026 16:00:00",
                result.getFirst().displayTime()
        );

        assertEquals(
                Instant.parse("2026-10-17T09:00:00Z"),
                result.getFirst().changedAt()
        );
    }

    @Test
    void foreignSellerCannotReadHistory() {

        prepareOrder();

        when(merchantApi.findShopSummary(SHOP_ID))
                .thenReturn(Optional.of(
                        shop(99L, true)
                ));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.forCurrentSeller(ORDER_CODE)
        );

        assertEquals(404, error.getStatusCode().value());

        verifyNoInteractions(histories);
    }

    @Test
    void lockedShopStillAllowsActiveOwnerToReadOldHistory() {

        prepareOrder();

        // ownerActive = true.
        // Shop không còn nhận đơn mới nhưng owner vẫn ACTIVE.
        when(merchantApi.findShopSummary(SHOP_ID))
                .thenReturn(Optional.of(
                        shop(SELLER_ID, true)
                ));

        prepareHistory();

        var result = service.forCurrentSeller(ORDER_CODE);

        assertEquals(3, result.size());
    }

    @Test
    void buyerCanReadOwnOrderHistory() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        BUYER_ID,
                        UserRole.USER,
                        AccountStatus.ACTIVE
                ));

        prepareOrder();
        prepareHistory();

        var result = service.forCurrentBuyer(ORDER_CODE);

        assertEquals(3, result.size());

        // Buyer không cần Merchant để đọc đơn của mình.
        verifyNoInteractions(merchantApi);
    }

    @Test
    void buyerCannotReadForeignOrder() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        999L,
                        UserRole.USER,
                        AccountStatus.ACTIVE
                ));

        prepareOrder();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.forCurrentBuyer(ORDER_CODE)
        );

        assertEquals(404, error.getStatusCode().value());

        verifyNoInteractions(histories);
    }

    @Test
    void activeAdminCanReadOrderHistory() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        50L,
                        UserRole.ADMIN,
                        AccountStatus.ACTIVE
                ));

        prepareOrder();
        prepareHistory();

        var result = service.forCurrentAdmin(ORDER_CODE);

        assertEquals(3, result.size());
    }

    @Test
    void normalUserCannotReadAdminHistory() {

        prepareOrder();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.forCurrentAdmin(ORDER_CODE)
        );

        assertEquals(403, error.getStatusCode().value());

        verifyNoInteractions(histories);
    }

    @Test
    void lockedAccountCannotReadHistory() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        SELLER_ID,
                        UserRole.USER,
                        AccountStatus.LOCKED
                ));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.forCurrentSeller(ORDER_CODE)
        );

        assertEquals(403, error.getStatusCode().value());

        verifyNoInteractions(orders, histories);
    }

    @Test
    void missingOrderDoesNotLeakHistory() {

        when(orders.findByOrderCode("MISSING"))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.forCurrentSeller("MISSING")
        );

        assertEquals(404, error.getStatusCode().value());

        verifyNoInteractions(histories);
    }

    private void prepareOrder() {

        Order order = new Order(
                ORDER_CODE,
                BUYER_ID,
                SHOP_ID,
                "Người nhận",
                "0900000000",
                "Địa chỉ thử nghiệm",
                null,
                new BigDecimal("150000.00")
        );

        ReflectionTestUtils.setField(
                order,
                "id",
                ORDER_ID
        );

        when(orders.findByOrderCode(ORDER_CODE))
                .thenReturn(Optional.of(order));
    }

    private void prepareOwnShop() {

        when(merchantApi.findShopSummary(SHOP_ID))
                .thenReturn(Optional.of(
                        shop(SELLER_ID, true)
                ));
    }

    private void prepareHistory() {

        var initial = history(
                1L,
                null,
                OrderStatus.PENDING,
                BUYER_ID,
                "Đơn hàng được tạo",
                "2026-10-17T09:00:00Z"
        );

        var confirmed = history(
                2L,
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED,
                SELLER_ID,
                "Chủ shop xác nhận đơn",
                "2026-10-17T09:15:00Z"
        );

        var cancelled = history(
                3L,
                OrderStatus.PREPARING,
                OrderStatus.CANCELLED,
                SELLER_ID,
                "Hủy đơn. Lý do: Sản phẩm bị lỗi",
                "2026-10-17T10:00:00Z"
        );

        when(histories.findAllByOrderIdOrderByChangedAtAscIdAsc(ORDER_ID))
                .thenReturn(List.of(
                        initial,
                        confirmed,
                        cancelled
                ));
    }

    private static OrderStatusHistory history(
            Long id,
            OrderStatus oldStatus,
            OrderStatus newStatus,
            Long actorId,
            String note,
            String changedAt
    ) {
        OrderStatusHistory history =
                new OrderStatusHistory(
                        ORDER_ID,
                        oldStatus,
                        newStatus,
                        actorId,
                        note
                );

        ReflectionTestUtils.setField(
                history,
                "id",
                id
        );

        ReflectionTestUtils.setField(
                history,
                "changedAt",
                Instant.parse(changedAt)
        );

        return history;
    }

    private static MerchantShopSummary shop(
            Long ownerId,
            boolean ownerActive
    ) {
        return new MerchantShopSummary(
                SHOP_ID,
                ownerId,
                "Shop thử nghiệm",
                ownerActive,
                true
        );
    }

    private static UserSummary actor(
            Long userId,
            UserRole role,
            AccountStatus status
    ) {
        return new UserSummary(
                userId,
                "Tài khoản thử nghiệm",
                role,
                status
        );
    }
}