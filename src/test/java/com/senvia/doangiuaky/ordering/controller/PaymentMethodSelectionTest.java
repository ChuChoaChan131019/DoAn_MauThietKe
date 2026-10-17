package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.dto.CheckoutSelectionView;
import com.senvia.doangiuaky.ordering.service.CheckoutSelectionService;
import com.senvia.doangiuaky.ordering.service.CheckoutPreviewService;
import com.senvia.doangiuaky.ordering.service.SellerOrderReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.senvia.doangiuaky.ordering.service.CheckoutTransactionService;
import com.senvia.doangiuaky.ordering.service.SellerPaymentConfirmationService;

@WebMvcTest(OrderingController.class)
@Import(SecurityConfig.class)
class PaymentMethodSelectionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutSelectionService checkoutSelectionService;

    @MockitoBean
    private CheckoutPreviewService checkoutPreviewService;

    @MockitoBean
    private CheckoutTransactionService checkoutTransactionService;

    @MockitoBean
    private SellerOrderReadService sellerOrderReadService;

    @MockitoBean
    private SellerPaymentConfirmationService sellerPaymentConfirmationService;

    @BeforeEach
    void setUp() {
        when(checkoutSelectionService.preview(anyList())).thenReturn(new CheckoutSelectionView(
                List.of(new CheckoutSelectionView.Shop(1L, "Shop",
                        List.of(new CheckoutSelectionView.Item(1L, "Product", 1, BigDecimal.ONE)),
                        BigDecimal.ONE)), BigDecimal.ONE));
    }

    @Test
    void acceptCOD() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("itemIds", "1")
                .param("receiverName", "Nguyen Van A")
                .param("receiverPhone", "0901234567")
                .param("shippingAddress", "123 Le Loi, TP HCM")
                .param("paymentMethod", "COD"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("checkoutPaymentForm",
                hasProperty("paymentMethod", is(PaymentMethod.COD))))
            .andExpect(model().attributeHasNoErrors("checkoutPaymentForm"));
        verify(checkoutPreviewService).preview(argThat(form -> form.getPaymentMethod() == PaymentMethod.COD),
                argThat(ids -> ids.equals(List.of(1L))));
    }

    @Test
    void acceptBankTransfer() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("itemIds", "1")
                .param("receiverName", "Nguyen Van A")
                .param("receiverPhone", "0901234567")
                .param("shippingAddress", "123 Le Loi, TP HCM")
                .param("paymentMethod", "BANK_TRANSFER"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("checkoutPaymentForm",
                hasProperty("paymentMethod", is(PaymentMethod.BANK_TRANSFER))))
            .andExpect(model().attributeHasNoErrors("checkoutPaymentForm"));
        verify(checkoutPreviewService).preview(argThat(form -> form.getPaymentMethod() == PaymentMethod.BANK_TRANSFER),
                argThat(ids -> ids.equals(List.of(1L))));
    }

    @Test
    void rejectMissingMethod() throws Exception {
        mockMvc.perform(post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("itemIds", "1")
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
                .param("itemIds", "1")
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
