package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderingController.class)
@Import(SecurityConfig.class)
class PaymentMethodSelectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptCOD() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("receiverName", "Nguyen Van A")
                .param("receiverPhone", "0901234567")
                .param("shippingAddress", "123 Le Loi, TP HCM")
                .param("paymentMethod", "COD"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("checkoutPaymentForm",
                hasProperty("paymentMethod", is(PaymentMethod.COD))))
            .andExpect(model().attribute("paymentValidated", true));
    }

    @Test
    void acceptBankTransfer() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("receiverName", "Nguyen Van A")
                .param("receiverPhone", "0901234567")
                .param("shippingAddress", "123 Le Loi, TP HCM")
                .param("paymentMethod", "BANK_TRANSFER"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("checkoutPaymentForm",
                hasProperty("paymentMethod", is(PaymentMethod.BANK_TRANSFER))))
            .andExpect(model().attribute("paymentValidated", true));
    }

    @Test
    void rejectMissingMethod() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("receiverName", "Nguyen Van A")
                .param("receiverPhone", "0901234567")
                .param("shippingAddress", "123 Le Loi, TP HCM"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors(
                "checkoutPaymentForm", "paymentMethod"));
    }

    @Test
    void rejectInvalidMethod() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("receiverName", "Nguyen Van A")
                .param("receiverPhone", "0901234567")
                .param("shippingAddress", "123 Le Loi, TP HCM")
                .param("paymentMethod", "TRANSFER"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors(
                "checkoutPaymentForm", "paymentMethod"));
    }

    @Test
    void rejectRequestWithoutCsrf() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("buyer").roles("USER"))
                .param("paymentMethod", "COD"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectAdmin() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("paymentMethod", "COD"))
            .andExpect(status().isForbidden());
    }
}
