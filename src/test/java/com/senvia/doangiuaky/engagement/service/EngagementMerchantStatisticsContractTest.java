package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.merchant.api.MerchantApi;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Engagement's dashboard reads only the public Merchant contracts. */
@SpringBootTest
@Transactional
class EngagementMerchantStatisticsContractTest {

    @Autowired private MerchantApi merchant;
    @Autowired private MerchantStatisticsApi statistics;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @WithMockUser(username = "demo-owner-nola@senvia.local")
    void dashboardCanReadOwnedShopAndManagedProductCount() {
        Long shopId = shopIdOf("demo-owner-nola@senvia.local");

        var shop = merchant.findShopSummary(shopId).orElseThrow();
        long count = statistics.countProductsForShop(shopId);

        assertEquals(shopId, shop.shopId());
        assertEquals("NOLA Store", shop.shopName());
        assertTrue(count > 0);
    }

    @Test
    @WithMockUser(username = "demo-owner-nola@senvia.local")
    void dashboardCannotReadAnotherOwnersCount() {
        Long shopId = shopIdOf("demo-owner-mori@senvia.local");

        assertThrows(MerchantStatisticsAccessException.class,
                () -> statistics.countProductsForShop(shopId));
    }

    private Long shopIdOf(String email) {
        return jdbc.queryForObject("""
                select s.id from shops s join users u on u.id = s.owner_id
                where u.email = ?
                """, Long.class, email);
    }
}
