package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderingController.class)
@Import(SecurityConfig.class)
class CheckoutDeliveryAddressTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsAndNormalizesDeliveryInformation() throws Exception {
        mockMvc.perform(validCheckout()
                .param("receiverName", "  Nguyen Van A  ")
                .param("receiverPhone", "  +84901234567  ")
                .param("shippingAddress", "  123 Le Loi, TP HCM  ")
                .param("note", "   "))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasNoErrors("checkoutPaymentForm"))
            .andExpect(model().attribute("checkoutPaymentForm", hasProperty("receiverName", is("Nguyen Van A"))))
            .andExpect(model().attribute("checkoutPaymentForm", hasProperty("receiverPhone", is("+84901234567"))))
            .andExpect(model().attribute("checkoutPaymentForm", hasProperty("shippingAddress", is("123 Le Loi, TP HCM"))))
            .andExpect(model().attribute("checkoutPaymentForm", hasProperty("note", nullValue())))
            .andExpect(model().attribute("addressValidated", true));
    }

    @Test
    void rejectsMissingDeliveryInformationAndShowsErrors() throws Exception {
        mockMvc.perform(validCheckout())
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("checkoutPaymentForm",
                "receiverName", "receiverPhone", "shippingAddress"))
            .andExpect(content().string(containsString("Vui lòng nhập tên người nhận.")))
            .andExpect(model().attributeDoesNotExist("addressValidated"));
    }

    @Test
    void rejectsWhitespaceOnlyDeliveryInformation() throws Exception {
        mockMvc.perform(validCheckout()
                .param("receiverName", "   ")
                .param("receiverPhone", "   ")
                .param("shippingAddress", "   "))
            .andExpect(model().attributeHasFieldErrors("checkoutPaymentForm",
                "receiverName", "receiverPhone", "shippingAddress"));
    }

    @Test
    void rejectsInvalidPhoneNumber() throws Exception {
        mockMvc.perform(validCheckout()
                .param("receiverName", "Nguyen Van A")
                .param("receiverPhone", "0(((((((((")
                .param("shippingAddress", "123 Le Loi, TP HCM"))
            .andExpect(model().attributeHasFieldErrors("checkoutPaymentForm", "receiverPhone"))
            .andExpect(model().attributeDoesNotExist("addressValidated"));
    }

    @Test
    void rejectsOversizedDeliveryInformation() throws Exception {
        mockMvc.perform(validCheckout()
                .param("receiverName", "A".repeat(101))
                .param("receiverPhone", "1".repeat(21))
                .param("shippingAddress", "A".repeat(2001))
                .param("note", "A".repeat(2001)))
            .andExpect(model().attributeHasFieldErrors("checkoutPaymentForm",
                "receiverName", "receiverPhone", "shippingAddress", "note"));
    }

    @Test
    void adminCannotOpenCheckout() throws Exception {
        mockMvc.perform(get("/checkout").with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder validCheckout() {
        return post("/checkout")
            .with(user("buyer").roles("USER"))
            .with(csrf())
            .param("paymentMethod", "COD");
    }
}
