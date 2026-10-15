package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.service.CheckoutPreparationException;
import com.senvia.doangiuaky.ordering.service.CheckoutPreviewService;
import com.senvia.doangiuaky.ordering.service.PreparedCheckout;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;

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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderingController.class)
@Import(SecurityConfig.class)
class CheckoutPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutPreviewService checkoutPreviewService;

    @Test
    void previewMultipleShopsSuccessfully() throws Exception {

        PreparedCheckout checkout = validTwoShopPreview();

        when(checkoutPreviewService.preview(
                any(CheckoutPaymentForm.class)
        )).thenReturn(checkout);

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("ordering/checkout/index"))
                .andExpect(model().attribute(
                        "checkoutPreview", checkout
                ))
                .andExpect(content().string(
                        containsString("Shop A")
                ))
                .andExpect(content().string(
                        containsString("Shop B")
                ))
                .andExpect(content().string(
                        containsString("550.000,00")
                ))
                .andExpect(content().string(
                        containsString("Chưa tạo đơn hàng")
                ));

        verify(checkoutPreviewService)
                .preview(any(CheckoutPaymentForm.class));
    }

    @Test
    void emptyCartShowsError() throws Exception {

        when(checkoutPreviewService.preview(
                any(CheckoutPaymentForm.class)
        )).thenThrow(
                new CheckoutPreparationException(
                        "Giỏ hàng đang trống."
                )
        );

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("ordering/checkout/index"))
                .andExpect(model().attribute(
                        "checkoutError",
                        "Giỏ hàng đang trống."
                ))
                .andExpect(model().attributeDoesNotExist(
                        "checkoutPreview"
                ));
    }

    @Test
    void unavailableProductShowsError() throws Exception {

        String message =
                "Sản phẩm đã ngừng bán hoặc không đủ hàng: 101";

        when(checkoutPreviewService.preview(
                any(CheckoutPaymentForm.class)
        )).thenThrow(
                new CheckoutPreparationException(message)
        );

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(model().attribute(
                        "checkoutError", message
                ))
                .andExpect(content().string(
                        containsString(message)
                ));
    }

    @Test
    void invalidFormDoesNotCallPreviewService() throws Exception {

        mockMvc.perform(
                post("/checkout")
                        .with(user("buyer").roles("USER"))
                        .with(csrf())
                        .param("receiverName", "")
                        .param("receiverPhone", "abc")
                        .param("shippingAddress", "")
                        .param("paymentMethod", "COD")
        )
        .andExpect(status().isOk())
        .andExpect(model().attributeHasFieldErrors(
                "checkoutPaymentForm",
                "receiverName",
                "receiverPhone",
                "shippingAddress"
        ));

        verifyNoInteractions(checkoutPreviewService);
    }

    private MockHttpServletRequestBuilder validRequest() {

        return post("/checkout")
                .with(user("buyer").roles("USER"))
                .with(csrf())
                .param("receiverName", "Nguyen Van A")
                .param("receiverPhone", "0901234567")
                .param("shippingAddress", "123 Le Loi, Da Lat")
                .param("paymentMethod", "COD");
    }

    private PreparedCheckout validTwoShopPreview() {

        var delivery = new PreparedCheckout.DeliverySnapshot(
                "Nguyen Van A",
                "0901234567",
                "123 Le Loi, Da Lat",
                null
        );

        var firstShop = new PreparedCheckout.ShopOrder(
                10L,
                21L,
                "Shop A",
                List.of(
                        new PreparedCheckout.Line(
                                101L,
                                "Tui vai",
                                new BigDecimal("100000.00"),
                                2,
                                new BigDecimal("200000.00")
                        )
                ),
                new BigDecimal("200000.00")
        );

        var secondShop = new PreparedCheckout.ShopOrder(
                10L,
                22L,
                "Shop B",
                List.of(
                        new PreparedCheckout.Line(
                                102L,
                                "Den trang tri",
                                new BigDecimal("350000.00"),
                                1,
                                new BigDecimal("350000.00")
                        )
                ),
                new BigDecimal("350000.00")
        );

        return new PreparedCheckout(
                10L,
                delivery,
                PaymentMethod.COD,
                List.of(firstShop, secondShop),
                new BigDecimal("550000.00"),
                List.of(
                        new CheckoutCartItem(1L, 101L, 2),
                        new CheckoutCartItem(2L, 102L, 1)
                )
        );
    }
}
