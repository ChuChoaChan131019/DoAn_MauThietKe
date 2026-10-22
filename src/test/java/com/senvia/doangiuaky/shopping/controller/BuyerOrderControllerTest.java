package com.senvia.doangiuaky.shopping.controller;

import com.senvia.doangiuaky.ordering.api.BuyerOrderApi;
import com.senvia.doangiuaky.ordering.api.OrderCancellationApi;
import com.senvia.doangiuaky.ordering.api.OrderDetails;
import com.senvia.doangiuaky.ordering.api.OrderHistoryEntry;
import com.senvia.doangiuaky.ordering.api.OrderPage;
import com.senvia.doangiuaky.ordering.api.OrderSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class BuyerOrderControllerTest {

    @Test
    void listUsesBuyerContractFixedPageSizeAndNormalizesFilter() throws Exception {
        BuyerOrderApi buyerOrders = mock(BuyerOrderApi.class);
        OrderCancellationApi cancellations = mock(OrderCancellationApi.class);
        OrderPage page = new OrderPage(
                List.of(summary("ORD-1", "CONFIRMED")),
                2,
                3,
                21
        );
        when(buyerOrders.listOwnOrders("CONFIRMED", 2, 10)).thenReturn(page);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new BuyerOrderController(buyerOrders, cancellations)
        ).build();

        mvc.perform(get("/account/orders")
                        .param("status", " confirmed ")
                        .param("page", "2")
                        .param("buyerId", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("shopping/orders"))
                .andExpect(model().attribute("orders", page.orders()))
                .andExpect(model().attribute("selectedStatus", "CONFIRMED"))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("totalPages", 3))
                .andExpect(model().attribute("totalElements", 21L));

        verify(buyerOrders).listOwnOrders(eq("CONFIRMED"), eq(2), eq(10));
    }

    @Test
    void detailUsesCombinedPublicDetailsContract() throws Exception {
        BuyerOrderApi buyerOrders = mock(BuyerOrderApi.class);
        OrderCancellationApi cancellations = mock(OrderCancellationApi.class);
        OrderDetails details = details("ORD-1", "PENDING");
        when(buyerOrders.getOwnOrder("ORD-1")).thenReturn(details);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new BuyerOrderController(buyerOrders, cancellations)
        ).build();

        mvc.perform(get("/account/orders/detail").param("id", "ORD-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("shopping/order-detail"))
                .andExpect(model().attribute("order", details))
                .andExpect(model().attribute("backUrl", "/account/orders"));

        verify(buyerOrders).getOwnOrder("ORD-1");
    }

    @Test
    void cancelSuccessUsesPostRedirectGetToDetail() throws Exception {
        BuyerOrderApi buyerOrders = mock(BuyerOrderApi.class);
        OrderCancellationApi cancellations = mock(OrderCancellationApi.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new BuyerOrderController(buyerOrders, cancellations)
        ).build();

        mvc.perform(post("/account/orders/cancel")
                        .param("id", "ORD-1")
                        .param("reason", "Đổi nhu cầu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/orders/detail?id=ORD-1"))
                .andExpect(flash().attribute("cancelSuccess", "Đã hủy đơn hàng."));

        verify(cancellations).cancelAsBuyer("ORD-1", "Đổi nhu cầu");
    }

    @Test
    void cancelValidationAndConflictBecomeFlashErrors() throws Exception {
        BuyerOrderApi buyerOrders = mock(BuyerOrderApi.class);
        OrderCancellationApi cancellations = mock(OrderCancellationApi.class);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do không hợp lệ."))
                .when(cancellations).cancelAsBuyer("ORD-1", " ");
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Đơn đã giao."))
                .when(cancellations).cancelAsBuyer("ORD-2", "Quá muộn");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new BuyerOrderController(buyerOrders, cancellations)
        ).build();

        mvc.perform(post("/account/orders/cancel")
                        .param("id", "ORD-1")
                        .param("reason", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/orders/detail?id=ORD-1"))
                .andExpect(flash().attribute("cancelError", "Lý do không hợp lệ."));

        mvc.perform(post("/account/orders/cancel")
                        .param("id", "ORD-2")
                        .param("reason", "Quá muộn"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/orders/detail?id=ORD-2"))
                .andExpect(flash().attribute("cancelError", "Đơn đã giao."));
    }

    @Test
    void authorizationErrorsAreNotConvertedToSuccess() throws Exception {
        BuyerOrderApi buyerOrders = mock(BuyerOrderApi.class);
        OrderCancellationApi cancellations = mock(OrderCancellationApi.class);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."))
                .when(cancellations).cancelAsBuyer("FOREIGN", "Không có quyền");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new BuyerOrderController(buyerOrders, cancellations)
        ).build();

        mvc.perform(post("/account/orders/cancel")
                        .param("id", "FOREIGN")
                        .param("reason", "Không có quyền"))
                .andExpect(status().isNotFound());
    }

    private static OrderSummary summary(String code, String status) {
        return new OrderSummary(
                code,
                10L,
                20L,
                status,
                new BigDecimal("120000"),
                Instant.parse("2026-10-22T01:00:00Z")
        );
    }

    private static OrderDetails details(String code, String status) {
        return new OrderDetails(
                summary(code, status),
                "Buyer Test",
                "0900000000",
                "Test address",
                "Gọi trước khi giao",
                new OrderDetails.PaymentInfo(
                        "BANK_TRANSFER",
                        "PAID",
                        new BigDecimal("120000"),
                        "SENVIA-ORD-1",
                        Instant.parse("2026-10-22T01:05:00Z"),
                        null
                ),
                List.of(new OrderDetails.Item(
                        1L,
                        2L,
                        "Snapshot product",
                        new BigDecimal("120000"),
                        1,
                        new BigDecimal("120000")
                )),
                List.of(new OrderHistoryEntry(
                        1L,
                        null,
                        status,
                        "Đơn hàng được tạo",
                        10L,
                        "Khởi tạo đơn hàng",
                        Instant.parse("2026-10-22T01:00:00Z"),
                        "22/10/2026 08:00:00"
                ))
        );
    }
}
