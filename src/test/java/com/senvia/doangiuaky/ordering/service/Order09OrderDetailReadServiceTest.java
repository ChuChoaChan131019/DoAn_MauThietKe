
package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;

import com.senvia.doangiuaky.ordering.dto.PaymentDetails;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Order09OrderDetailReadServiceTest {

    private static final String ORDER_CODE = "ORD-SEC-001";

    private static final Long ORDER_ID = 100L;
    private static final Long BUYER_ID = 10L;
    private static final Long OTHER_BUYER_ID = 20L;
    private static final Long ADMIN_ID = 30L;

    private final CurrentUserApi currentUserApi =
            mock(CurrentUserApi.class);

    private final MerchantApi merchantApi = mock(MerchantApi.class);

    private final OrderRepository orders =
            mock(OrderRepository.class);

    private final OrderItemRepository items =
            mock(OrderItemRepository.class);

    private final PaymentQueryService payments =
            mock(PaymentQueryService.class);

    private final OrderDetailReadService service =
            new OrderDetailReadService(
                    currentUserApi,
                    merchantApi,
                    orders,
                    items,
                    payments
            );

    @BeforeEach
    void setup() {
        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        BUYER_ID,
                        UserRole.USER,
                        AccountStatus.ACTIVE
                ));
    }

    @Test
    void buyerCanReadOwnOrderAndPayment() {

        prepareOrder();
        preparePayment();

        var result = service.getBuyerOrder(ORDER_CODE);

        assertEquals(ORDER_CODE, result.id());
        assertEquals("PENDING", result.status());

        assertEquals(
                PaymentMethod.BANK_TRANSFER.name(),
                result.payment().method()
        );

        assertEquals(
                PaymentStatus.PENDING.name(),
                result.payment().status()
        );

        verify(items)
                .findAllByOrderIdOrderByIdAsc(ORDER_ID);

        verify(payments)
                .getByOrderId(ORDER_ID);
    }

    @Test
    void foreignBuyerCannotReadOrder() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        OTHER_BUYER_ID,
                        UserRole.USER,
                        AccountStatus.ACTIVE
                ));

        prepareOrder();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getBuyerOrder(ORDER_CODE)
        );

        assertEquals(
                404,
                exception.getStatusCode().value()
        );

        // ORDER 09:
        // Không được truy vấn items hoặc payment
        // khi người mua không sở hữu đơn.
        verifyNoInteractions(items, payments);
    }

    @Test
    void changingOrderCodeCannotBypassOwnership() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        OTHER_BUYER_ID,
                        UserRole.USER,
                        AccountStatus.ACTIVE
                ));

        when(orders.findByOrderCode("ORD-FOREIGN"))
                .thenReturn(Optional.of(order()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getBuyerOrder("ORD-FOREIGN")
        );

        assertEquals(404, exception.getStatusCode().value());

        verifyNoInteractions(items, payments);
    }

    @Test
    void adminCannotUseBuyerAccess() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        ADMIN_ID,
                        UserRole.ADMIN,
                        AccountStatus.ACTIVE
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getBuyerOrder(ORDER_CODE)
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void activeAdminCanReadOrderForModeration() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        ADMIN_ID,
                        UserRole.ADMIN,
                        AccountStatus.ACTIVE
                ));

        prepareOrder();
        preparePayment();

        var result = service.getAdminOrder(ORDER_CODE);

        assertEquals(ORDER_CODE, result.id());

        assertEquals(
                PaymentStatus.PENDING.name(),
                result.payment().status()
        );
    }

    @Test
    void normalUserCannotUseAdminAccess() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getAdminOrder(ORDER_CODE)
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void lockedBuyerCannotReadOwnOrder() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        BUYER_ID,
                        UserRole.USER,
                        AccountStatus.LOCKED
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getBuyerOrder(ORDER_CODE)
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void lockedAdminCannotReadOrder() {

        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(
                        ADMIN_ID,
                        UserRole.ADMIN,
                        AccountStatus.LOCKED
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getAdminOrder(ORDER_CODE)
        );

        assertEquals(403, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void missingOrderReturns404() {

        when(orders.findByOrderCode("MISSING"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getBuyerOrder("MISSING")
        );

        assertEquals(404, exception.getStatusCode().value());

        verifyNoInteractions(items, payments);
    }

    @Test
    void invalidOrderCodeDoesNotQueryDatabase() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getBuyerOrder("  ")
        );

        assertEquals(404, exception.getStatusCode().value());

        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void buyerListQueriesOnlyOwnOrdersAndDoesNotLoadPrivateDetails() {
        when(orders.findAllByBuyerIdOrderByCreatedAtDescIdDesc(BUYER_ID))
                .thenReturn(List.of(order()));

        var result = service.listForCurrentBuyer();

        assertEquals(1, result.size());
        assertEquals(ORDER_CODE, result.getFirst().orderCode());
        assertEquals(BUYER_ID, result.getFirst().buyerId());
        verify(orders).findAllByBuyerIdOrderByCreatedAtDescIdDesc(BUYER_ID);
        verify(orders, never()).findAll(any(Sort.class));
        verifyNoInteractions(items, payments);
    }

    @Test
    void activeAdminCanListOrders() {
        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(ADMIN_ID, UserRole.ADMIN, AccountStatus.ACTIVE));
        when(orders.findAll(any(Sort.class))).thenReturn(List.of(order()));

        var result = service.listForCurrentAdmin();

        assertEquals(ORDER_CODE, result.getFirst().orderCode());
        verify(orders).findAll(any(Sort.class));
        verifyNoInteractions(items, payments);
    }

    @Test
    void buyerCannotUseAdminList() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, service::listForCurrentAdmin);
        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(orders, items, payments);
    }

    @Test
    void lockedBuyerCannotQueryList() {
        when(currentUserApi.requireCurrentUser())
                .thenReturn(actor(BUYER_ID, UserRole.USER, AccountStatus.LOCKED));
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, service::listForCurrentBuyer);
        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(orders, items, payments);
    }

    private void prepareOrder() {
        when(orders.findByOrderCode(ORDER_CODE))
                .thenReturn(Optional.of(order()));
    }

    private void preparePayment() {

        when(items.findAllByOrderIdOrderByIdAsc(ORDER_ID))
                .thenReturn(List.of());

        when(payments.getByOrderId(ORDER_ID))
                .thenReturn(
                        new PaymentDetails(
                                ORDER_ID,
                                PaymentMethod.BANK_TRANSFER,
                                PaymentStatus.PENDING,
                                new BigDecimal("150000.00"),
                                "SENVIA-SEC-001",
                                null,
                                null
                        )
                );
    }

    private static Order order() {

        Order order = new Order(
                ORDER_CODE,
                BUYER_ID,
                50L,
                "Người nhận thử nghiệm",
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

        ReflectionTestUtils.setField(
                order,
                "createdAt",
                Instant.parse("2026-10-17T09:00:00Z")
        );

        return order;
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