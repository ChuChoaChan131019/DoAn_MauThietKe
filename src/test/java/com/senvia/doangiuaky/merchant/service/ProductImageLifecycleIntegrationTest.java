package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.dto.ProductForm;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductImageRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ProductImageLifecycleIntegrationTest {

    @Autowired
    private ProductManagementService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MerchantImageStorage imageStorage;

    @Test
    void createsAndReplacesProductImagesWithoutExposingPublicIdToTheView() {
        Fixture fixture = fixture();
        Category category = categoryRepository.saveAndFlush(
                Category.create("Image category " + fixture.suffix(), null));
        when(imageStorage.uploadProductImage(eq(fixture.ownerId()), any()))
                .thenReturn(
                        new MerchantImageAsset("https://cloudinary.test/first.png", "products/first"),
                        new MerchantImageAsset("https://cloudinary.test/second.png", "products/second"));

        ProductForm createForm = form(category.getId(), image("first.png"));
        var created = productService.createWithImages(fixture.ownerId(), createForm);
        var persistedBeforeUpdate = productImageRepository
                .findAllByProductIdOrderByDisplayOrderAscIdAsc(created.id());

        assertEquals(1, persistedBeforeUpdate.size());
        assertEquals("https://cloudinary.test/first.png", persistedBeforeUpdate.get(0).getImageUrl());
        assertEquals("products/first", persistedBeforeUpdate.get(0).getPublicId());
        assertEquals(0, created.images().get(0).displayOrder());
        assertEquals("https://cloudinary.test/first.png", created.images().get(0).imageUrl());

        ProductForm updateForm = form(category.getId(), image("second.png"));
        updateForm.setRemovedImageIds(List.of(persistedBeforeUpdate.get(0).getId()));
        var updated = productService.updateWithImages(fixture.ownerId(), created.id(), updateForm);

        var persistedAfterUpdate = productImageRepository
                .findAllByProductIdOrderByDisplayOrderAscIdAsc(created.id());
        assertEquals(1, persistedAfterUpdate.size());
        assertEquals("https://cloudinary.test/second.png", persistedAfterUpdate.get(0).getImageUrl());
        assertEquals("products/second", persistedAfterUpdate.get(0).getPublicId());
        assertEquals(1, updated.images().size());
        assertEquals("https://cloudinary.test/second.png", updated.images().get(0).imageUrl());
    }

    @Test
    void cannotRemoveTheOnlyProductImage() {
        Fixture fixture = fixture();
        Category category = categoryRepository.saveAndFlush(
                Category.create("Required image category " + fixture.suffix(), null));
        when(imageStorage.uploadProductImage(eq(fixture.ownerId()), any()))
                .thenReturn(new MerchantImageAsset("https://cloudinary.test/only.png", "products/only"));
        var created = productService.createWithImages(fixture.ownerId(), form(category.getId(), image("only.png")));

        ProductForm updateForm = form(category.getId());
        updateForm.setRemovedImageIds(List.of(productImageRepository
                .findAllByProductIdOrderByDisplayOrderAscIdAsc(created.id()).get(0).getId()));

        assertThrows(ProductImageManagementException.class,
                () -> productService.updateWithImages(fixture.ownerId(), created.id(), updateForm));
    }

    private ProductForm form(Long categoryId, MockMultipartFile... images) {
        ProductForm form = new ProductForm("Product", "Description", categoryId,
                new BigDecimal("10.00"), 2);
        form.setImages(List.of(images));
        return form;
    }

    private static MockMultipartFile image(String filename) {
        return new MockMultipartFile(
                "images",
                filename,
                "image/png",
                java.util.Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "product-image-" + suffix + "@example.test";
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status,
                                   created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Product Image User", email, "test-password-hash");
        Long ownerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, email);
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   created_at, updated_at)
                values (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, "Product image shop " + suffix, "0900000000", "Test address",
                ShopStatus.APPROVED.name());
        Long shopId = jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);
        return new Fixture(ownerId, shopId, suffix);
    }

    private record Fixture(Long ownerId, Long shopId, String suffix) {
    }
}
