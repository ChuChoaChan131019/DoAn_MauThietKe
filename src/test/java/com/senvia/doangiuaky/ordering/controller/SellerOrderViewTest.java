package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.ordering.dto.PaymentDetails;
import com.senvia.doangiuaky.ordering.dto.SellerOrderView;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.service.SellerOrderReadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SellerOrderViewTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    SellerOrderReadService sellerOrders;

    @Test
    void listRendersItemsAndPaymentFromReadModel() throws Exception {
        when(sellerOrders.listOwnOrders(null)).thenReturn(List.of(order()));

        mvc.perform(get("/merchant/orders").with(user("owner@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sản phẩm thử")))
                .andExpect(content().string(containsString("BANK_TRANSFER")))
                .andExpect(content().string(not(containsString("cancelOrderForm"))));
    }

    @Test
    void detailRendersCurrentPaymentStatusAndReference() throws Exception {
        when(sellerOrders.getOwnOrder("ORD-1")).thenReturn(order());

        mvc.perform(get("/merchant/orders/detail")
                        .param("id", "ORD-1")
                        .with(user("owner@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("REF-1")))
                .andExpect(content().string(containsString("Đã thanh toán")));
    }

    private static SellerOrderView order() {
        BigDecimal amount = new BigDecimal("120000.00");
        PaymentDetails payment = new PaymentDetails(21L, PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PAID, amount, "REF-1", null, null);
        SellerOrderView.Item item = new SellerOrderView.Item(5L, "Sản phẩm thử",
                amount, 1, amount);
        return new SellerOrderView("ORD-1", "16/10/2026 07:00", "Người nhận",
                "0900000000", "Địa chỉ", "PENDING", amount, payment, List.of(item));
    }
}
