package com.senvia.doangiuaky.merchant.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.DataBinder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPriceStockFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPositivePriceAndZeroStock() {
        assertTrue(validate(new ProductPriceStockForm(new BigDecimal("0.01"), 0)).isEmpty());
    }

    @Test
    void rejectsMissingPriceAndStockAtTheirFields() {
        Set<ConstraintViolation<ProductPriceStockForm>> violations =
                validate(new ProductPriceStockForm(null, null));

        assertTrue(hasFieldError(violations, "price"));
        assertTrue(hasFieldError(violations, "stockQuantity"));
    }

    @Test
    void rejectsZeroNegativeAndOverPrecisionPrices() {
        assertFalse(validate(new ProductPriceStockForm(BigDecimal.ZERO, 1)).isEmpty());
        assertFalse(validate(new ProductPriceStockForm(new BigDecimal("-1.00"), 1)).isEmpty());
        assertFalse(validate(new ProductPriceStockForm(new BigDecimal("1.001"), 1)).isEmpty());
        assertFalse(validate(new ProductPriceStockForm(new BigDecimal("10000000000.00"), 1)).isEmpty());
    }

    @Test
    void rejectsNegativeStockButAllowsZero() {
        assertFalse(validate(new ProductPriceStockForm(new BigDecimal("10.00"), -1)).isEmpty());
        assertTrue(validate(new ProductPriceStockForm(new BigDecimal("10.00"), 0)).isEmpty());
    }

    @Test
    void reportsTypeMismatchForNonNumericInput() {
        ProductPriceStockForm form = new ProductPriceStockForm();
        DataBinder binder = new DataBinder(form);

        binder.bind(new MutablePropertyValues(Map.of(
                "price", "not-a-number",
                "stockQuantity", "1.5")));

        assertTrue(binder.getBindingResult().hasFieldErrors("price"));
        assertTrue(binder.getBindingResult().hasFieldErrors("stockQuantity"));
    }

    private Set<ConstraintViolation<ProductPriceStockForm>> validate(ProductPriceStockForm form) {
        return validator.validate(form);
    }

    private static boolean hasFieldError(Set<ConstraintViolation<ProductPriceStockForm>> violations,
                                         String field) {
        return violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().equals(field));
    }
}
