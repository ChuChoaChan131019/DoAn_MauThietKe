package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.dto.AdminCategoryView;
import com.senvia.doangiuaky.merchant.dto.CategoryForm;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryManagementServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final Long USER_ID = 8L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private IdentityApi identityApi;

    private CategoryManagementService service;

    @BeforeEach
    void setUp() {
        service = new CategoryManagementService(categoryRepository, identityApi);
        lenient().when(identityApi.findUser(ADMIN_ID))
                .thenReturn(Optional.of(new UserSummary(ADMIN_ID, "Admin", UserRole.ADMIN, AccountStatus.ACTIVE)));
    }

    @Test
    void activeAdminCanCreateCategory() {
        when(categoryRepository.existsByName("Nhà cửa")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminCategoryView result = service.create(
                ADMIN_ID, new CategoryForm("  Nhà cửa  ", "  Đồ dùng  "));

        assertEquals("Nhà cửa", result.name());
        assertEquals("Đồ dùng", result.description());
        assertTrue(result.active());
        verify(categoryRepository).saveAndFlush(any(Category.class));
    }

    @Test
    void nonAdminCannotMutateCategories() {
        when(identityApi.findUser(USER_ID))
                .thenReturn(Optional.of(new UserSummary(USER_ID, "User", UserRole.USER, AccountStatus.ACTIVE)));

        assertThrows(CategoryAuthorizationException.class,
                () -> service.create(USER_ID, new CategoryForm("Category", null)));
        verify(categoryRepository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void lockedAdminCannotMutateCategories() {
        when(identityApi.findUser(USER_ID))
                .thenReturn(Optional.of(new UserSummary(USER_ID, "Locked admin", UserRole.ADMIN, AccountStatus.LOCKED)));

        assertThrows(CategoryAuthorizationException.class,
                () -> service.create(USER_ID, new CategoryForm("Category", null)));
        verify(categoryRepository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void duplicateNameIsRejectedBeforeSaving() {
        when(categoryRepository.existsByName("Nhà cửa")).thenReturn(true);

        assertThrows(CategoryDuplicateNameException.class,
                () -> service.create(ADMIN_ID, new CategoryForm("Nhà cửa", null)));
        verify(categoryRepository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void activeAdminCanUpdateAndHideCategoryWithoutDeletingIt() {
        Category category = Category.create("Cũ", "Mô tả cũ");
        CategoryForm form = new CategoryForm("Mới", "Mô tả mới");
        when(categoryRepository.findById(11L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot("Mới", 11L)).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdminCategoryView updated = service.update(ADMIN_ID, 11L, form);
        service.hide(ADMIN_ID, 11L);

        assertEquals("Mới", updated.name());
        assertFalse(category.isActive());
        verify(categoryRepository, times(2)).saveAndFlush(category);
    }

    @Test
    void updateRejectsDuplicateNameOfAnotherCategory() {
        Category category = Category.create("Cũ", null);
        when(categoryRepository.findById(11L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameAndIdNot("Đã có", 11L)).thenReturn(true);

        assertThrows(CategoryDuplicateNameException.class,
                () -> service.update(ADMIN_ID, 11L, new CategoryForm("Đã có", null)));
        verify(categoryRepository, never()).saveAndFlush(any(Category.class));
    }

    @Test
    void inactiveCategoryCannotBeSelectedForNewProduct() {
        Category category = Category.create("Hidden", null);
        category.hide();
        when(categoryRepository.findById(12L)).thenReturn(Optional.of(category));

        assertThrows(InactiveCategoryException.class,
                () -> service.requireActiveCategory(12L));
    }

    @Test
    void activeSelectionExcludesHiddenCategories() {
        Category active = Category.create("Active", null);
        when(categoryRepository.findAllByActiveTrueOrderByNameAscIdAsc()).thenReturn(List.of(active));

        assertEquals(List.of(active), service.listActiveForProductSelection());
    }
}
