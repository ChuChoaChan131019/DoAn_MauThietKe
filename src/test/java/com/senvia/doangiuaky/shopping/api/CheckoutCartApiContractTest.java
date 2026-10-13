package com.senvia.doangiuaky.shopping.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckoutCartApiContractTest {

    @Test
    void snapshotDtoExposesOnlyCartItemProductAndQuantity() {
        assertTrue(CheckoutCartItem.class.isRecord());
        assertEquals(List.of("cartItemId", "productId", "quantity"),
                Arrays.stream(CheckoutCartItem.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList());
        assertEquals(List.of(Long.class, Long.class, int.class),
                Arrays.stream(CheckoutCartItem.class.getRecordComponents())
                        .map(RecordComponent::getType)
                        .toList());
    }

    @Test
    void orderingCanMockThePublicContractWithoutShoppingInternals() {
        CheckoutCartApi checkoutCartApi = mock(CheckoutCartApi.class);
        List<CheckoutCartItem> snapshot = List.of(new CheckoutCartItem(11L, 21L, 2));
        when(checkoutCartApi.getCheckoutSnapshot()).thenReturn(snapshot);

        assertEquals(snapshot, checkoutCartApi.getCheckoutSnapshot());
    }

    @Test
    void publicContractDoesNotExposeBuyerId() {
        assertEquals(0, method("getCheckoutSnapshot").getParameterCount());
        Method cleanup = method("removePurchasedItems");
        assertEquals(1, cleanup.getParameterCount());
        assertEquals(List.class, cleanup.getParameterTypes()[0]);
    }

    private static Method method(String name) {
        return Arrays.stream(CheckoutCartApi.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
