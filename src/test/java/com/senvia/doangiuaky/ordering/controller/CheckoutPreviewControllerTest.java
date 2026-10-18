package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;
import com.senvia.doangiuaky.ordering.dto.CheckoutSelectionView;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.service.CheckoutPreparationException;
import com.senvia.doangiuaky.ordering.service.CheckoutPreviewService;
import com.senvia.doangiuaky.ordering.service.CheckoutSelectionException;
import com.senvia.doangiuaky.ordering.service.CheckoutSelectionService;
import com.senvia.doangiuaky.ordering.service.SellerOrderReadService;
import com.senvia.doangiuaky.ordering.service.PreparedCheckout;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.senvia.doangiuaky.ordering.service.CheckoutTransactionService;
import com.senvia.doangiuaky.ordering.service.SellerPaymentConfirmationService;
import com.senvia.doangiuaky.ordering.service.SellerOrderStatusService;
import com.senvia.doangiuaky.ordering.service.OrderCancellationService;
import com.senvia.doangiuaky.ordering.api.OrderHistoryApi;
import com.senvia.doangiuaky.ordering.api.OrderQueryApi;

@WebMvcTest(OrderingController.class)
@Import(SecurityConfig.class)
class CheckoutPreviewControllerTest {

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

    @MockitoBean
    private SellerOrderStatusService sellerOrderStatusService;

    @MockitoBean
    private OrderCancellationService orderCancellationService;

    @MockitoBean
    private OrderHistoryApi orderHistoryApi;

    @MockitoBean
    private OrderQueryApi orderQueryApi;

    @BeforeEach
    void selection() {
        when(checkoutSelectionService.preview(List.of("1", "2"))).thenReturn(new CheckoutSelectionView(
                List.of(
                        new CheckoutSelectionView.Shop(21L, "Shop A",
                                List.of(new CheckoutSelectionView.Item(1L, "Tui vai", 2, new BigDecimal("200000.00"))),
                                new BigDecimal("200000.00")),
                        new CheckoutSelectionView.Shop(22L, "Shop B",
                                List.of(new CheckoutSelectionView.Item(2L, "Den trang tri", 1, new BigDecimal("350000.00"))),
                                new BigDecimal("350000.00"))), new BigDecimal("550000.00")));
    }

    @Test
    void displaysPreparedPreviewForOnlySelectedItems() throws Exception {
        PreparedCheckout prepared = new PreparedCheckout(10L,
                new PreparedCheckout.DeliverySnapshot("Preview Buyer", "0901234567", "123 Test Street", null),
                PaymentMethod.COD,
                List.of(
                        new PreparedCheckout.ShopOrder(10L, 21L, "Shop A",
                                List.of(new PreparedCheckout.Line(101L, "Tui vai", new BigDecimal("100000.00"), 2,
                                        new BigDecimal("200000.00"))), new BigDecimal("200000.00")),
                        new PreparedCheckout.ShopOrder(10L, 22L, "Shop B",
                                List.of(new PreparedCheckout.Line(102L, "Den trang tri", new BigDecimal("350000.00"), 1,
                                        new BigDecimal("350000.00"))), new BigDecimal("350000.00"))),
                new BigDecimal("550000.00"),
                List.of(new CheckoutCartItem(1L, 101L, 2), new CheckoutCartItem(2L, 102L, 1)));
        when(checkoutPreviewService.preview(any(CheckoutPaymentForm.class), eq(List.of(1L, 2L)))).thenReturn(prepared);

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("ordering/checkout/index"))
                .andExpect(model().attribute("checkoutPreview", prepared))
                .andExpect(content().string(containsString("Shop A")))
                .andExpect(content().string(containsString("Shop B")))
                .andExpect(content().string(containsString("550,000₫")))
                .andExpect(content().string(containsString("Xác nhận đặt hàng theo giá đã xem")));

        verify(checkoutPreviewService).preview(any(CheckoutPaymentForm.class), eq(List.of(1L, 2L)));
        verifyNoInteractions(checkoutTransactionService);
    }

    @Test
    void failedPreparationShowsErrorWithoutSuccessPreview() throws Exception {
        when(checkoutPreviewService.preview(any(CheckoutPaymentForm.class), eq(List.of(1L, 2L))))
                .thenThrow(new CheckoutPreparationException("Sản phẩm đã ngừng bán hoặc không đủ hàng: 101"));

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("checkoutPreview"))
                .andExpect(content().string(containsString("Sản phẩm đã ngừng bán hoặc không đủ hàng: 101")));
    }

    @Test
    void invalidSelectionCannotReachPreparation() throws Exception {
        when(checkoutSelectionService.preview(List.of("999")))
                .thenThrow(new CheckoutSelectionException("Sản phẩm được chọn không còn trong giỏ hàng của bạn."));

        mockMvc.perform(post("/checkout")
                        .with(user("buyer").roles("USER"))
                        .with(csrf())
                        .param("itemIds", "999")
                        .param("receiverName", "Preview Buyer")
                        .param("receiverPhone", "0901234567")
                        .param("shippingAddress", "123 Test Street")
                        .param("paymentMethod", "COD"))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("checkoutPreview"))
                .andExpect(content().string(containsString("Sản phẩm được chọn không còn trong giỏ hàng của bạn.")));
        verifyNoInteractions(checkoutPreviewService);
    }

    @Test
    void invalidDeliveryCannotReachPreparation() throws Exception {
        mockMvc.perform(post("/checkout")
                        .with(user("buyer").roles("USER"))
                        .with(csrf())
                        .param("itemIds", "1", "2")
                        .param("receiverName", "")
                        .param("receiverPhone", "abc")
                        .param("shippingAddress", "")
                        .param("paymentMethod", "COD"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("checkoutPaymentForm",
                        "receiverName", "receiverPhone", "shippingAddress"));
        verifyNoInteractions(checkoutPreviewService);
    }

    private MockHttpServletRequestBuilder validRequest() {
        return post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("itemIds", "1", "2")
                .param("receiverName", "Preview Buyer")
                .param("receiverPhone", "0901234567")
                .param("shippingAddress", "123 Test Street")
                .param("paymentMethod", "COD");
    }
}
