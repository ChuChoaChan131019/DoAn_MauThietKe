package com.senvia.doangiuaky.merchant.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTest {

    @Test
    void createsActiveCategoryWithNormalizedText() {
        Category category = Category.create("  Nhà cửa  ", "  Đồ dùng cho nhà  ");

        assertEquals("Nhà cửa", category.getName());
        assertEquals("Đồ dùng cho nhà", category.getDescription());
        assertTrue(category.isActive());
    }

    @Test
    void blankOrOversizedNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Category.create("  ", null));
        assertThrows(IllegalArgumentException.class, () -> Category.create("a".repeat(101), null));
    }

    @Test
    void updateKeepsCategoryActiveAndHideIsSoftAndIdempotent() {
        Category category = Category.create("Cũ", "Mô tả cũ");

        category.updateDetails("  Mới  ", "  Mô tả mới  ");
        assertEquals("Mới", category.getName());
        assertEquals("Mô tả mới", category.getDescription());
        assertTrue(category.isActive());

        category.hide();
        category.hide();

        assertFalse(category.isActive());
    }
}
