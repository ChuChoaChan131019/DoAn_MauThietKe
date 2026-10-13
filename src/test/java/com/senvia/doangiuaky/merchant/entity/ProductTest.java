package com.senvia.doangiuaky.merchant.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductTest {

    @Test
    void createsProductWithPositivePriceAndZeroStock() {
        Product product = product(new BigDecimal("0.01"), 0);

        assertEquals(new BigDecimal("0.01"), product.getPrice());
        assertEquals(0, product.getStockQuantity());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
    }

    @Test
    void acceptsMaximumNumericPrecisionAndTwoDecimalPlaces() {
        Product product = product(new BigDecimal("9999999999.99"), 7);

        assertEquals(new BigDecimal("9999999999.99"), product.getPrice());
    }

    @ParameterizedTest(name = "rejects price {0}")
    @MethodSource("invalidPrices")
    void rejectsInvalidPrices(BigDecimal price) {
        assertThrows(IllegalArgumentException.class, () -> product(price, 1));
    }

    private static Stream<BigDecimal> invalidPrices() {
        return Stream.of(
                (BigDecimal) null,
                BigDecimal.ZERO,
                new BigDecimal("-0.01"),
                new BigDecimal("0.001"),
                new BigDecimal("9999999999.999"),
                new BigDecimal("10000000000.00"));
    }

    @Test
    void rejectsNullOrNegativeStock() {
        assertThrows(IllegalArgumentException.class, () -> product(new BigDecimal("10.00"), null));
        assertThrows(IllegalArgumentException.class, () -> product(new BigDecimal("10.00"), -1));
    }

    @Test
    void rejectsNullOrBlankDescription() {
        assertThrows(IllegalArgumentException.class, () -> Product.create(
                10L, 20L, "Test product", null, new BigDecimal("10.00"), 1));
        assertThrows(IllegalArgumentException.class, () -> Product.create(
                10L, 20L, "Test product", "   ", new BigDecimal("10.00"), 1));
    }

    @Test
    void invalidUpdateLeavesExistingPriceAndStockUnchanged() {
        Product product = product(new BigDecimal("125.50"), 8);

        assertThrows(IllegalArgumentException.class,
                () -> product.changePriceAndStock(new BigDecimal("0.001"), -1));

        assertEquals(new BigDecimal("125.50"), product.getPrice());
        assertEquals(8, product.getStockQuantity());
    }

    @Test
    void validUpdateChangesPriceAndStockTogether() {
        Product product = product(new BigDecimal("125.50"), 8);

        product.changePriceAndStock(new BigDecimal("99.90"), 0);

        assertEquals(new BigDecimal("99.90"), product.getPrice());
        assertEquals(0, product.getStockQuantity());
    }

    @Test
    void hiddenProductCanBeShownAgain() {
        Product product = product(new BigDecimal("10.00"), 2);

        product.hide();
        product.show();

        assertEquals(ProductStatus.ACTIVE, product.getStatus());
    }

    private static Product product(BigDecimal price, Integer stockQuantity) {
        return Product.create(
                10L,
                20L,
                "Test product",
                "Product used by the domain tests",
                price,
                stockQuantity);
    }
}
