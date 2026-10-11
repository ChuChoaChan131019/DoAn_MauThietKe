package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.merchant.dto.AdminCategoryView;
import com.senvia.doangiuaky.merchant.dto.CategoryForm;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class CategoryManagementService {

    private final CategoryRepository categoryRepository;
    private final IdentityApi identityApi;

    public CategoryManagementService(CategoryRepository categoryRepository, IdentityApi identityApi) {
        this.categoryRepository = categoryRepository;
        this.identityApi = identityApi;
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryView> listAll(Long adminId) {
        requireActiveAdmin(adminId);
        return categoryRepository.findAllByOrderByNameAscIdAsc().stream()
                .map(CategoryManagementService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCategoryView getForAdmin(Long adminId, Long categoryId) {
        requireActiveAdmin(adminId);
        return toView(findCategory(categoryId));
    }

    @Transactional
    public AdminCategoryView create(Long adminId, CategoryForm form) {
        requireActiveAdmin(adminId);
        Objects.requireNonNull(form, "Category form must not be null");

        String name = normalizeName(form.getName());
        if (categoryRepository.existsByName(name)) {
            throw duplicateName();
        }

        try {
            Category category = categoryRepository.saveAndFlush(
                    Category.create(name, normalizeDescription(form.getDescription())));
            return toView(category);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateName();
        }
    }

    @Transactional
    public AdminCategoryView update(Long adminId, Long categoryId, CategoryForm form) {
        requireActiveAdmin(adminId);
        Objects.requireNonNull(form, "Category form must not be null");

        Category category = findCategory(categoryId);
        String name = normalizeName(form.getName());
        if (categoryRepository.existsByNameAndIdNot(name, categoryId)) {
            throw duplicateName();
        }

        category.updateDetails(name, normalizeDescription(form.getDescription()));
        try {
            return toView(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateName();
        }
    }

    @Transactional
    public void hide(Long adminId, Long categoryId) {
        requireActiveAdmin(adminId);
        Category category = findCategory(categoryId);
        category.hide();
        categoryRepository.saveAndFlush(category);
    }

    @Transactional(readOnly = true)
    public List<Category> listActiveForProductSelection() {
        return categoryRepository.findAllByActiveTrueOrderByNameAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Category requireActiveCategory(Long categoryId) {
        Category category = findCategory(categoryId);
        if (!category.isActive()) {
            throw new InactiveCategoryException("Danh mục đã bị ẩn và không thể dùng cho sản phẩm mới.");
        }
        return category;
    }

    private void requireActiveAdmin(Long adminId) {
        if (adminId == null || identityApi.findUser(adminId)
                .filter(user -> user.role() == UserRole.ADMIN
                        && user.accountStatus() == AccountStatus.ACTIVE)
                .isEmpty()) {
            throw new CategoryAuthorizationException(
                    "Chỉ quản trị viên đang hoạt động mới được quản lý danh mục.");
        }
    }

    private Category findCategory(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new CategoryNotFoundException("Không tìm thấy danh mục.");
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Không tìm thấy danh mục."));
    }

    private static AdminCategoryView toView(Category category) {
        return new AdminCategoryView(
                category.getId(), category.getName(), category.getDescription(), category.isActive());
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        return name.trim();
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static CategoryDuplicateNameException duplicateName() {
        return new CategoryDuplicateNameException("Tên danh mục đã tồn tại.");
    }
}
