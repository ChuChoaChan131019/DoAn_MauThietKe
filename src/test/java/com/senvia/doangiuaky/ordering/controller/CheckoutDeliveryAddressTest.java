package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.identity.security.SecurityConfig;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.senvia.doangiuaky.ordering.service.CheckoutTransactionService;
import com.senvia.doangiuaky.ordering.service.SellerPaymentConfirmationService;
import com.senvia.doangiuaky.ordering.service.SellerOrderStatusService;
import com.senvia.doangiuaky.ordering.service.OrderCancellationService;
import com.senvia.doangiuaky.ordering.api.OrderHistoryApi;
import com.senvia.doangiuaky.ordering.api.OrderQueryApi;
import com.senvia.doangiuaky.ordering.api.BuyerOrderApi;

@WebMvcTest(OrderingController.class)
@Import(SecurityConfig.class)
class CheckoutDeliveryAddressTest {
    @MockitoBean
    private CheckoutTransactionService checkoutTransactionService;

    @MockitoBean
    private SellerOrderReadService sellerOrderReadService;

    @MockitoBean
    private SellerPaymentConfirmationService sellerPaymentConfirmationService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutSelectionService checkoutSelectionService;

    @MockitoBean
    private CheckoutPreviewService checkoutPreviewService;

    @MockitoBean
    private SellerOrderStatusService sellerOrderStatusService;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    @MockitoBean
    private OrderHistoryApi orderHistoryApi;

    @MockitoBean
    private OrderQueryApi orderQueryApi;

    @MockitoBean
    private BuyerOrderApi buyerOrderApi;

    @BeforeEach
    void setUp() {
        when(checkoutSelectionService.preview(anyList())).thenReturn(new CheckoutSelectionView(
                List.of(new CheckoutSelectionView.Shop(1L, "Shop",
                        List.of(new CheckoutSelectionView.Item(1L, "Product", 1, BigDecimal.ONE)),
                        BigDecimal.ONE)), BigDecimal.ONE));
    }

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
            .andExpect(model().attribute("checkoutPaymentForm", hasProperty("note", nullValue())));
        verify(checkoutPreviewService).preview(argThat(form ->
                "Nguyen Van A".equals(form.getReceiverName())
                    && "+84901234567".equals(form.getReceiverPhone())
                    && "123 Le Loi, TP HCM".equals(form.getShippingAddress())
                    && form.getNote() == null), argThat(ids -> ids.equals(List.of(1L))));
    }

    @Test
    void rejectsMissingDeliveryInformationAndShowsErrors() throws Exception {
        mockMvc.perform(validCheckout())
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("checkoutPaymentForm",
                "receiverName", "receiverPhone", "shippingAddress"))
            .andExpect(content().string(containsString("Vui lòng nhập tên người nhận.")))
            .andExpect(model().attributeDoesNotExist("checkoutPreview"));
        verifyNoInteractions(checkoutPreviewService);
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
            .andExpect(model().attributeDoesNotExist("checkoutPreview"));
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
            .param("itemIds", "1")
            .param("paymentMethod", "COD");
    }
}
