package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.api.MerchantStatisticsAccessException;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class MerchantStatisticsIntegrationTest {

    @Autowired private MerchantStatisticsApi statistics;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @WithMockUser(username = "demo-owner-nola@senvia.local")
    void countsHiddenAndOutOfStockProductsOwnedByCurrentUser() {
        Long shopId = jdbc.queryForObject("""
                select s.id from shops s join users u on u.id = s.owner_id
                where u.email = 'demo-owner-nola@senvia.local'
                """, Long.class);
        Long productId = jdbc.queryForObject(
                "select min(id) from products where shop_id = ?", Long.class, shopId);
        jdbc.update("update products set status = 'HIDDEN', stock_quantity = 0 where id = ?", productId);

        long expected = jdbc.queryForObject("select count(*) from products where shop_id = ?", Long.class, shopId);
        assertEquals(expected, statistics.countProductsForShop(shopId));
    }

    @Test
    @WithMockUser(username = "demo-owner-nola@senvia.local")
    void rejectsAnotherOwnersShop() {
        Long otherShopId = jdbc.queryForObject("""
                select s.id from shops s join users u on u.id = s.owner_id
                where u.email = 'demo-owner-mori@senvia.local'
                """, Long.class);
        assertThrows(MerchantStatisticsAccessException.class,
                () -> statistics.countProductsForShop(otherShopId));
    }
}
