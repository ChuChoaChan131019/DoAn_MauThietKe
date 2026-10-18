package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.ordering.api.AdminOrderApi;
import com.senvia.doangiuaky.ordering.api.OrderCancellationApi;
import com.senvia.doangiuaky.ordering.api.OrderDetails;
import com.senvia.doangiuaky.ordering.api.OrderPage;
import com.senvia.doangiuaky.ordering.api.OrderSummary;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOrderControllerTest {

    private final AdminOrderApi adminOrderApi = mock(AdminOrderApi.class);
    private final OrderCancellationApi orderCancellationApi = mock(OrderCancellationApi.class);
    private final AdminOrderController controller =
            new AdminOrderController(adminOrderApi, orderCancellationApi);

    @Test
    void sendsAllFiltersAndPaginationToOrderingApi() {
        OrderPage page = new OrderPage(List.of(), 2, 3, 45);
        when(adminOrderApi.listOrders("PENDING", 5L, 7L, "ord", 2, 20)).thenReturn(page);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("engagement/admin-orders",
                controller.orders("PENDING", 5L, 7L, "ord", 2, model));

        assertEquals(page, model.getAttribute("ordersPage"));
        verify(adminOrderApi).listOrders("PENDING", 5L, 7L, "ord", 2, 20);
    }

    @Test
    void getsAdminDetailOnlyThroughOrderingContract() {
        OrderDetails details = new OrderDetails(
                new OrderSummary("ORD-1", 3L, 4L, "PENDING", BigDecimal.TEN, Instant.EPOCH),
                "Receiver", "0900000000", "Address", null, null, List.of(), List.of());
        when(adminOrderApi.getOrder("ORD-1")).thenReturn(details);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("engagement/admin-order-detail", controller.orderDetails("ORD-1", model));
        assertEquals(details, model.getAttribute("orderDetails"));
        verify(adminOrderApi).getOrder("ORD-1");
    }

    @Test
    void delegatesCancellationToOrderingContractAndUsesPostRedirectGet() {
        org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes =
                new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();

        assertEquals("redirect:/engagement/admin/orders/ORD-1",
                controller.cancelOrder("ORD-1", "Duplicate order", redirectAttributes));

        verify(orderCancellationApi).cancelAsAdmin("ORD-1", "Duplicate order");
    }
}
