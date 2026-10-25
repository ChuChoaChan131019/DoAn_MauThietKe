package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.ordering.api.AdminOrderApi;
import com.senvia.doangiuaky.ordering.api.OrderCancellationApi;
import com.senvia.doangiuaky.ordering.api.OrderDetails;
import com.senvia.doangiuaky.ordering.api.OrderHistoryEntry;
import com.senvia.doangiuaky.ordering.api.OrderPage;
import com.senvia.doangiuaky.ordering.api.OrderSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminOrderController.class)
@Import(SecurityConfig.class)
class AdminOrderControllerTest {

    private static final String MONITOR_URL = "/admin/orders/monitor";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOrderApi adminOrderApi;

    @MockitoBean
    private OrderCancellationApi orderCancellationApi;

    @Test
    void adminCanFilterAndPaginateOrdersThroughOrderingApi() throws Exception {
        OrderPage orderPage = new OrderPage(
                List.of(order("ORD-2026-001", "PREPARING")),
                2,
                3,
                21);
        when(adminOrderApi.listOrders("PREPARING", 12L, 34L, "ORD-2026", 2, 10))
                .thenReturn(orderPage);

        mockMvc.perform(get(MONITOR_URL)
                        .with(user("admin").roles("ADMIN"))
                        .param("status", "PREPARING")
                        .param("buyerId", "12")
                        .param("shopId", "34")
                        .param("orderCode", "ORD-2026")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/admin-orders"))
                .andExpect(model().attribute("orderPage", orderPage))
                .andExpect(model().attribute("selectedStatus", "PREPARING"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ORD-2026-001")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Trang 2 / 3")));

        verify(adminOrderApi).listOrders("PREPARING", 12L, 34L, "ORD-2026", 2, 10);
    }

    @Test
    void adminCanViewOrderItemsPaymentAndHistoryThroughPublicDetailsDto() throws Exception {
        OrderDetails details = details("ORD-2026-001", "PENDING");
        when(adminOrderApi.getOrder("ORD-2026-001")).thenReturn(details);

        mockMvc.perform(get(MONITOR_URL + "/ORD-2026-001")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/admin-order-detail"))
                .andExpect(model().attribute("orderDetails", details))
                .andExpect(model().attribute("canCancel", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bàn phím cơ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("COD_PENDING")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đơn được tạo")));

        verify(adminOrderApi).getOrder("ORD-2026-001");
    }

    @Test
    void cancelFormDelegatesToOrderingAndRedirectsToOrderDetails() throws Exception {
        mockMvc.perform(post(MONITOR_URL + "/ORD-2026-001/cancel")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("reason", "Đơn có dấu hiệu gian lận"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(MONITOR_URL + "/ORD-2026-001"))
                .andExpect(flash().attribute("cancelSuccess", "Đã gửi yêu cầu hủy đơn."));

        verify(orderCancellationApi).cancelAsAdmin("ORD-2026-001", "Đơn có dấu hiệu gian lận");
    }

    @Test
    void cancellationConflictIsShownAndReasonIsPreserved() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Đơn không thể hủy ở trạng thái hiện tại."))
                .when(orderCancellationApi).cancelAsAdmin("ORD-2026-001", "Lý do");

        mockMvc.perform(post(MONITOR_URL + "/ORD-2026-001/cancel")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("reason", "Lý do"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(MONITOR_URL + "/ORD-2026-001"))
                .andExpect(flash().attribute("cancelError", "Đơn không thể hủy ở trạng thái hiện tại."))
                .andExpect(flash().attribute("cancelReason", "Lý do"));
    }

    @Test
    void invalidCancellationReasonIsShownByTheEngagementView() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do hủy không hợp lệ."))
                .when(orderCancellationApi).cancelAsAdmin("ORD-2026-001", " ");

        mockMvc.perform(post(MONITOR_URL + "/ORD-2026-001/cancel")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("reason", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(MONITOR_URL + "/ORD-2026-001"))
                .andExpect(flash().attribute("cancelError", "Lý do hủy không hợp lệ."))
                .andExpect(flash().attribute("cancelReason", " "));
    }

    @Test
    void buyerAndShopOwnerCannotAccessAdminOrderRoutes() throws Exception {
        mockMvc.perform(get(MONITOR_URL).with(user("buyer").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(MONITOR_URL + "/ORD-2026-001").with(user("shop-owner").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(MONITOR_URL + "/ORD-2026-001/cancel")
                        .with(user("shop-owner").roles("USER"))
                        .with(csrf())
                        .param("reason", "Lý do"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminOrderApi, orderCancellationApi);
    }

    @Test
    void completedOrderDoesNotShowCancelForm() throws Exception {
        when(adminOrderApi.getOrder("ORD-2026-001")).thenReturn(details("ORD-2026-001", "COMPLETED"));

        mockMvc.perform(get(MONITOR_URL + "/ORD-2026-001")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canCancel", false))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Xác nhận hủy đơn"))));

        verify(orderCancellationApi, never()).cancelAsAdmin("ORD-2026-001", "Lý do");
    }

    private static OrderSummary order(String code, String status) {
        return new OrderSummary(
                code,
                12L,
                34L,
                status,
                new BigDecimal("1250000"),
                Instant.parse("2026-10-20T10:15:00Z"));
    }

    private static OrderDetails details(String code, String status) {
        return new OrderDetails(
                order(code, status),
                "Nguyễn Văn A",
                "0900000000",
                "1 Đường Mẫu, TP.HCM",
                null,
                new OrderDetails.PaymentInfo(
                        "COD",
                        "COD_PENDING",
                        new BigDecimal("1250000"),
                        null,
                        null,
                        null),
                List.of(new OrderDetails.Item(
                        101L,
                        201L,
                        "Bàn phím cơ",
                        new BigDecimal("1250000"),
                        1,
                        new BigDecimal("1250000"))),
                List.of(new OrderHistoryEntry(
                        1L,
                        null,
                        "PENDING",
                        "Đơn được tạo",
                        12L,
                        "Khởi tạo đơn hàng",
                        Instant.parse("2026-10-20T10:15:00Z"),
                        "20/10/2026 17:15:00")));
    }
}
