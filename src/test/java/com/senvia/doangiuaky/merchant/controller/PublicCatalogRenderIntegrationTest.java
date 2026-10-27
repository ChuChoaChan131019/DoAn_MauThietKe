package com.senvia.doangiuaky.merchant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicCatalogRenderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void guestCanRenderCatalogDetailAndPublicShopWithRealData() throws Exception {
        Long productId = productId("Canvas Tote Bag");
        Long shopId = shopId(productId);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Linen Shirt"))));
        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOLA Store")))
                .andExpect(content().string(containsString("/products/" + productId + "/reviews")))
                .andExpect(content().string(containsString("/merchant/shop-public?shopId=" + shopId)))
                .andExpect(content().string(containsString("https://")));
        mockMvc.perform(get("/merchant/shop-public").param("shopId", shopId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOLA Store")))
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Linen Shirt"))));
    }

    @Test
    void homeLikedProductsSectionShowsTenProductsInAutoplayCarousel() throws Exception {
        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Top sản phẩm được yêu thích")))
                .andReturn().getResponse().getContentAsString();

        java.util.regex.Matcher section = java.util.regex.Pattern.compile(
                "(?s)<section[^>]*data-home-section=\"liked\"[^>]*>(.*?)</section>")
                .matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(section.find());
        assertEquals(10, section.group(1).split("data-carousel-slide", -1).length - 1);
        org.junit.jupiter.api.Assertions.assertTrue(section.group(1).contains("data-home-carousel"));
        org.junit.jupiter.api.Assertions.assertTrue(section.group(1).contains("data-carousel-previous"));
        org.junit.jupiter.api.Assertions.assertTrue(section.group(1).contains("data-carousel-next"));
    }

    @Test
    void homeNewArrivalsSectionShowsTenProductsInAutoplayCarousel() throws Exception {
        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mới trên Senvia")))
                .andReturn().getResponse().getContentAsString();

        java.util.regex.Matcher section = java.util.regex.Pattern.compile(
                "(?s)<section[^>]*data-home-section=\"new-arrivals\"[^>]*>(.*?)</section>")
                .matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(section.find());
        assertEquals(10, section.group(1).split("data-carousel-slide", -1).length - 1);
        org.junit.jupiter.api.Assertions.assertTrue(section.group(1).contains("data-carousel-previous"));
        org.junit.jupiter.api.Assertions.assertTrue(section.group(1).contains("data-carousel-next"));
    }

    @Test
    void catalogShowsTwelveSaleableProductsBeforePagination() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("products", hasSize(12)))
                .andExpect(model().attribute("totalPages", 10))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(content().string(containsString("page=2")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(not(containsString("&larr;"))))
                .andExpect(content().string(not(containsString("&rarr;"))));

        mockMvc.perform(get("/products").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("products", hasSize(12)))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("aria-current=\"page\"")));

        mockMvc.perform(get("/products").param("page", "10"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("products", hasSize(11)))
                .andExpect(model().attribute("currentPage", 10));
    }

    @Test
    void catalogSupportsBoundedPageSizesAndPreservesSizeInPageLinks() throws Exception {
        mockMvc.perform(get("/products").param("size", "24"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("products", hasSize(24)))
                .andExpect(model().attribute("totalPages", 5))
                .andExpect(model().attribute("pageSize", 24))
                .andExpect(content().string(containsString("name=\"size\" value=\"24\"")))
                .andExpect(content().string(containsString("page=2&amp;size=24")));

        mockMvc.perform(get("/products").param("size", "24").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("products", hasSize(23)))
                .andExpect(model().attribute("currentPage", 5));

        mockMvc.perform(get("/products").param("size", "48").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("products", hasSize(23)))
                .andExpect(model().attribute("currentPage", 3));
    }

    @Test
    void catalogReportsInvalidPageAndSizeWithoutAnUnboundedQuery() throws Exception {
        mockMvc.perform(get("/products").param("page", "abc").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterErrors", true))
                .andExpect(model().attribute("products", hasSize(12)))
                .andExpect(model().attribute("pageSize", 12))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(content().string(containsString("Số trang phải là số nguyên")))
                .andExpect(content().string(containsString("Số sản phẩm mỗi trang phải là 12, 24 hoặc 48.")));

        mockMvc.perform(get("/products").param("page", "0").param("size", "không hợp lệ"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 12))
                .andExpect(model().attribute("currentPage", 1));
    }

    @Test
    void demoProductsAreDistributedAcrossAllActiveCategories() {
        Long demoProductCount = jdbcTemplate.queryForObject(
                """
                select count(distinct p.id) from products p
                join product_images i on i.product_id = p.id
                where i.public_id like 'senvia/merchant/product-images/demo/demo-%'
                   or i.public_id like 'senvia/merchant/product-images/demo/technology/%'
                """, Long.class);
        Long placeholderNameCount = jdbcTemplate.queryForObject(
                "select count(*) from products where name like 'Senvia Demo %' or name like '%Sản phẩm tuyển chọn%'",
                Long.class);
        Long productsWithoutImages = jdbcTemplate.queryForObject("""
                select count(*) from products p
                where not exists (select 1 from product_images i where i.product_id = p.id)
                """, Long.class);
        Long duplicateSeededImages = jdbcTemplate.queryForObject("""
                select count(*) - count(distinct i.image_url)
                from product_images i
                where i.public_id like 'senvia/merchant/product-images/demo/%'
                """, Long.class);
        Integer categoriesWithoutProducts = jdbcTemplate.queryForObject("""
                select count(*) from categories c
                where not exists (
                    select 1 from products p
                    where p.category_id = c.id and p.status = 'ACTIVE' and p.stock_quantity > 0
                )
                """, Integer.class);
        Integer productCountSpread = jdbcTemplate.queryForObject("""
                select max(category_products.product_count) - min(category_products.product_count)
                from (
                    select c.id, count(p.id) as product_count
                    from categories c
                    left join products p on p.category_id = c.id
                        and exists (select 1 from product_images i where i.product_id = p.id
                            and (i.public_id like 'senvia/merchant/product-images/demo/demo-%'
                                or i.public_id like 'senvia/merchant/product-images/demo/technology/%'))
                    group by c.id
                ) category_products
                """, Integer.class);

        assertEquals(100L, demoProductCount);
        assertEquals(0L, placeholderNameCount);
        assertEquals(0L, productsWithoutImages);
        assertEquals(0L, duplicateSeededImages);
        Long cloudinaryDemoImages = jdbcTemplate.queryForObject("""
                select count(*) from product_images
                where public_id like 'senvia/merchant/product-images/demo/%'
                  and image_url like 'https://res.cloudinary.com/%/image/upload/%'
                """, Long.class);
        assertEquals(120L, cloudinaryDemoImages);
        assertEquals(0, categoriesWithoutProducts);
        assertEquals(1, productCountSpread);
    }

    @Test
    void technologyDemoMigrationGivesMatchingNamesAndCloudinaryImages() {
        Long shopId = shopId(productId("Canvas Tote Bag"));
        jdbcTemplate.update("""
                insert into categories (name, description, active, created_at, updated_at)
                values ('Công nghệ', 'Danh mục kiểm thử.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long techCategoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = 'Công nghệ'", Long.class);
        Long[] ids = new Long[2];
        for (int index = 0; index < 2; index++) {
            String genericName = "Senvia Demo " + (index + 13) + " - Sản phẩm tuyển chọn - Công nghệ";
            jdbcTemplate.update("""
                    insert into products (shop_id, category_id, name, description, price,
                                          stock_quantity, status, created_at, updated_at)
                    values (?, ?, ?, 'Sản phẩm mẫu.', 1000000.00, 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, shopId, techCategoryId, genericName);
            ids[index] = productId(genericName);
            jdbcTemplate.update("""
                    insert into product_images (product_id, image_url, public_id, display_order, created_at)
                    values (?, ?, ?, 0, CURRENT_TIMESTAMP)
                    """, ids[index], "https://example.test/old-" + index,
                    "senvia/merchant/product-images/demo/old-" + index);
        }

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/h2/V202610151800__merchant_name_technology_demo_products.sql"));
            return null;
        });

        assertEquals("Tai nghe Apple AirPods", jdbcTemplate.queryForObject(
                "select name from products where id = ?", String.class, ids[0]));
        assertEquals("Điện thoại iPhone 13 Pro", jdbcTemplate.queryForObject(
                "select name from products where id = ?", String.class, ids[1]));
        assertEquals("senvia/merchant/product-images/demo/technology/tech-01",
                jdbcTemplate.queryForObject("select public_id from product_images where product_id = ?",
                        String.class, ids[0]));
        assertEquals("senvia/merchant/product-images/demo/technology/tech-02",
                jdbcTemplate.queryForObject("select public_id from product_images where product_id = ?",
                        String.class, ids[1]));
    }

    @Test
    void nameCleanupCoversRemainingGenericProductsWithoutChangingMerchantProducts() {
        Long shopId = shopId(productId("Canvas Tote Bag"));
        Long categoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = 'Thời trang'", Long.class);
        String genericName = "Senvia Demo 13 - Sản phẩm tuyển chọn - Thời trang";
        String merchantName = "Senvia Demo 999 - Sản phẩm tuyển chọn - Thời trang";
        for (String name : new String[]{genericName, merchantName}) {
            jdbcTemplate.update("""
                    insert into products (shop_id, category_id, name, description, price,
                                          stock_quantity, status, created_at, updated_at)
                    values (?, ?, ?, 'Sản phẩm demo được tuyển chọn cho danh mục Thời trang.',
                            100000.00, 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, shopId, categoryId, name);
        }
        Long genericId = productId(genericName);
        Long merchantId = productId(merchantName);
        jdbcTemplate.update("""
                insert into product_images (product_id, image_url, public_id, display_order, created_at)
                values (?, 'https://example.test/demo.jpg',
                        'senvia/merchant/product-images/demo/demo-013', 0, CURRENT_TIMESTAMP)
                """, genericId);
        jdbcTemplate.update("""
                insert into product_images (product_id, image_url, public_id, display_order, created_at)
                values (?, 'https://example.test/merchant.jpg', 'merchant-upload-999', 0, CURRENT_TIMESTAMP)
                """, merchantId);

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/h2/V202610151900__merchant_clean_demo_product_names.sql"));
            return null;
        });

        assertEquals("Túi đeo chéo", jdbcTemplate.queryForObject(
                "select name from products where id = ?", String.class, genericId));
        assertEquals(merchantName, jdbcTemplate.queryForObject(
                "select name from products where id = ?", String.class, merchantId));
    }

    @Test
    void userCanRenderCatalogAndDetail() throws Exception {
        Long productId = productId("Canvas Tote Bag");

        mockMvc.perform(get("/products").with(user("buyer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")));
        mockMvc.perform(get("/products/{id}", productId).with(user("buyer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOLA Store")));
    }

    @Test
    void shopFilterUsesShopParameterAndAcceptsLegacyBrandLinks() throws Exception {
        mockMvc.perform(get("/products").param("shop", "NOLA Store"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Brass Table Lamp"))));
        mockMvc.perform(get("/products").param("brand", "NOLA Store"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Brass Table Lamp"))));
    }

    @Test
    void categoryPriceAndSearchFiltersCombineWithInclusiveDecimalPrices() throws Exception {
        Long productId = productId("Canvas Tote Bag");
        Long categoryId = categoryId(productId);
        jdbcTemplate.update("update products set price = ? where id = ?",
                new java.math.BigDecimal("1190000.25"), productId);

        mockMvc.perform(get("/products")
                        .param("categoryId", categoryId.toString())
                        .param("min", "1190000.25")
                        .param("max", "1190000.25")
                        .param("q", "canvas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Leather Wallet"))))
                .andExpect(content().string(not(containsString("Brass Table Lamp"))));

        mockMvc.perform(get("/products")
                        .param("categoryId", categoryId.toString())
                        .param("min", "1190000.26"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Canvas Tote Bag"))));
    }

    @Test
    void catalogSearchIsAvailableFromDesktopAndMobileHeaderForms() throws Exception {
        mockMvc.perform(get("/products").param("q", "canvas"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"header-product-search\"")))
                .andExpect(content().string(containsString("id=\"mobile-product-search\"")))
                .andExpect(content().string(containsString("name=\"q\"")))
                .andExpect(content().string(containsString("Tìm theo tên sản phẩm...")))
                .andExpect(content().string(containsString("value=\"canvas\"")));
    }

    @Test
    void catalogRejectsSearchKeywordLongerThanProductNameLimit() throws Exception {
        mockMvc.perform(get("/products").param("q", "a".repeat(201)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterErrors", true))
                .andExpect(content().string(containsString("Từ khóa tìm kiếm không được vượt quá 200 ký tự.")));
    }

    @Test
    void activeFilterButtonsRemoveOnlyTheirOwnFilter() throws Exception {
        Long categoryId = categoryId(productId("Canvas Tote Bag"));
        Long secondCategoryId = categoryId(productId("Brass Table Lamp"));
        String html = mockMvc.perform(get("/products")
                        .param("categoryIds", categoryId.toString(), secondCategoryId.toString())
                        .param("q", "canvas")
                        .param("min", "0")
                        .param("max", "2000000")
                        .param("shop", "NOLA Store", "MORI Store")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bộ lọc đang áp dụng")))
                .andExpect(content().string(containsString("Trang chủ / Sản phẩm / Sản phẩm thuộc 2 danh mục")))
                .andReturn().getResponse().getContentAsString();

        String categoryForm = filterRemovalForm(html, "id=\"category-filter\"");
        assertTrue(categoryForm.contains("name=\"categoryIds\" value=\"" + categoryId + "\""));
        assertTrue(categoryForm.contains("name=\"categoryIds\" value=\"" + secondCategoryId + "\""));
        assertEquals(2, categoryForm.split("products-category-chip-selected", -1).length - 1);
        assertTrue(categoryForm.contains("name=\"q\" value=\"canvas\""));
        assertTrue(categoryForm.contains("name=\"min\" value=\"0\""));
        assertTrue(categoryForm.contains("name=\"max\" value=\"2000000\""));
        assertTrue(categoryForm.contains("name=\"shop\" value=\"NOLA Store\""));
        assertTrue(categoryForm.contains("name=\"shop\" value=\"MORI Store\""));
        assertTrue(categoryForm.contains("name=\"sort\" value=\"price_asc\""));

        String searchForm = filterRemovalForm(html, "remove-search-filter");
        assertFalse(searchForm.contains("name=\"q\""));
        assertTrue(searchForm.contains("name=\"categoryIds\" value=\"" + categoryId + "\""));
        assertTrue(searchForm.contains("name=\"categoryIds\" value=\"" + secondCategoryId + "\""));
        assertTrue(searchForm.contains("name=\"min\""));
        assertTrue(searchForm.contains("name=\"max\""));
        assertTrue(searchForm.contains("name=\"shop\""));

        String priceForm = filterRemovalForm(html, "remove-price-filter");
        assertFalse(priceForm.contains("name=\"min\""));
        assertFalse(priceForm.contains("name=\"max\""));
        assertTrue(priceForm.contains("name=\"categoryIds\" value=\"" + categoryId + "\""));
        assertTrue(priceForm.contains("name=\"q\""));
        assertTrue(priceForm.contains("name=\"shop\""));

        String shopForm = filterRemovalForm(html, "data-remove-shop=\"NOLA Store\"");
        assertTrue(shopForm.contains("name=\"shop\" value=\"MORI Store\""));
        assertFalse(shopForm.contains("name=\"shop\" value=\"NOLA Store\""));
        assertTrue(shopForm.contains("name=\"categoryIds\" value=\"" + categoryId + "\""));
        assertTrue(shopForm.contains("name=\"q\""));
        assertTrue(shopForm.contains("name=\"min\""));
        assertTrue(shopForm.contains("name=\"max\""));

        String otherShopForm = filterRemovalForm(html, "data-remove-shop=\"MORI Store\"");
        assertTrue(otherShopForm.contains("name=\"shop\" value=\"NOLA Store\""));
        assertFalse(otherShopForm.contains("name=\"shop\" value=\"MORI Store\""));
        assertTrue(otherShopForm.contains("name=\"categoryIds\" value=\"" + secondCategoryId + "\""));
    }

    @Test
    void catalogCanFilterSeveralCategoriesTogether() throws Exception {
        Long fashionCategoryId = categoryId(productId("Canvas Tote Bag"));
        Long homeCategoryId = categoryId(productId("Brass Table Lamp"));

        mockMvc.perform(get("/products").param("categoryIds",
                        fashionCategoryId.toString(), homeCategoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(containsString("Brass Table Lamp")))
                .andExpect(content().string(not(containsString("Signature Parfum"))))
                .andExpect(content().string(containsString("products-category-chip-selected")));
    }

    private String filterRemovalForm(String html, String attribute) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?s)<form[^>]*" + java.util.regex.Pattern.quote(attribute) + "[^>]*>(.*?)</form>")
                .matcher(html);
        assertTrue(matcher.find(), "Không tìm thấy form bỏ lọc: " + attribute);
        return matcher.group(1);
    }

    @Test
    void legacyCategoryNameStillFiltersOnlyActiveCategory() throws Exception {
        mockMvc.perform(get("/products").param("category", "Thời trang"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Brass Table Lamp"))));
    }

    @Test
    void invalidPriceFiltersRenderValidationMessagesAndPreserveInput() throws Exception {
        mockMvc.perform(get("/products").param("min", "10").param("max", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá tối thiểu không được lớn hơn giá tối đa.")))
                .andExpect(content().string(containsString("value=\"10\"")))
                .andExpect(content().string(containsString("value=\"5\"")));

        mockMvc.perform(get("/products").param("min", "khong-phai-so"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá tối thiểu không đúng định dạng số.")));

        mockMvc.perform(get("/products").param("categoryId", "khong-phai-id"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Danh mục không hợp lệ.")));

        mockMvc.perform(get("/products").param("max", "-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá phải lớn hơn hoặc bằng 0.")));

        mockMvc.perform(get("/products").param("min", "1.234"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá tối đa có 10 chữ số phần nguyên và 2 chữ số thập phân.")));

        mockMvc.perform(get("/products").param("max", "10000000000.00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá tối đa có 10 chữ số phần nguyên và 2 chữ số thập phân.")));
    }

    @Test
    void hiddenCategoryAndItsProductsAreNotExposedByFilter() throws Exception {
        Long productId = productId("Canvas Tote Bag");
        Long categoryId = categoryId(productId);
        jdbcTemplate.update("update categories set active = false where id = ?", categoryId);

        mockMvc.perform(get("/products").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Danh mục không khả dụng.")))
                .andExpect(content().string(not(containsString("Canvas Tote Bag"))))
                .andExpect(content().string(not(containsString("/products?categoryId=" + categoryId))));
    }

    @Test
    void unknownLegacyCategoryNameIsNotEchoedToPage() throws Exception {
        mockMvc.perform(get("/products").param("category", "Unknown hidden category"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Danh mục không khả dụng.")))
                .andExpect(content().string(not(containsString("Unknown hidden category"))));
    }

    @Test
    void homeCategoryLinksUseStableCategoryIds() throws Exception {
        Long productId = productId("Canvas Tote Bag");
        Long categoryId = categoryId(productId);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/products?categoryId=" + categoryId)));
    }

    @Test
    void headerAndHomeCategoryItemsUseActiveDatabaseCategories() throws Exception {
        jdbcTemplate.update("""
                insert into categories (name, description, active, created_at, updated_at)
                values (?, ?, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Danh mục mới", "Danh mục được thêm trong kiểm thử.");
        Long activeCategoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = ?", Long.class, "Danh mục mới");
        jdbcTemplate.update("""
                insert into categories (name, description, active, created_at, updated_at)
                values (?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Danh mục đã ẩn", "Không được xuất hiện ở giao diện công khai.");

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mới về")))
                .andExpect(content().string(containsString("Bán chạy")))
                .andExpect(content().string(containsString("Danh mục mới")))
                .andExpect(content().string(containsString("/products?categoryId=" + activeCategoryId)))
                .andExpect(content().string(not(containsString("Danh mục đã ẩn"))))
                .andExpect(content().string(not(containsString("Công nghệ"))))
                .andExpect(content().string(not(containsString("Lifestyle"))));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Danh mục mới")))
                .andExpect(content().string(containsString("/products?categoryId=" + activeCategoryId)))
                .andExpect(content().string(not(containsString("Danh mục đã ẩn"))));
    }

    @Test
    void lockedShopIsAbsentFromCatalogAndBothPublicDetailsReturnNotFound() throws Exception {
        Long productId = productId("Canvas Tote Bag");
        Long categoryId = categoryId(productId);
        Long shopId = shopId(productId);
        Long ownerId = jdbcTemplate.queryForObject(
                "select owner_id from shops where id = ?", Long.class, shopId);
        jdbcTemplate.update("""
                update shops set status = 'LOCKED', lock_reason = 'test',
                       locked_by = ?, locked_at = CURRENT_TIMESTAMP where id = ?
                """, ownerId, shopId);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Canvas Tote Bag"))));
        mockMvc.perform(get("/products").param("categoryId", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Canvas Tote Bag"))));
        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/merchant/shop-public").param("shopId", shopId.toString()))
                .andExpect(status().isNotFound());
    }

    private Long productId(String name) {
        return jdbcTemplate.queryForObject("select id from products where name = ?", Long.class, name);
    }

    private Long shopId(Long productId) {
        return jdbcTemplate.queryForObject(
                "select shop_id from products where id = ?", Long.class, productId);
    }

    private Long categoryId(Long productId) {
        return jdbcTemplate.queryForObject(
                "select category_id from products where id = ?", Long.class, productId);
    }
}
