package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class ShopLockServiceIntegrationTest {

    @Autowired
    private ShopLockService shopLockService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void lockAndUnlockRoundTripPersistsAuditMetadataAndPreservesApprovalAudit() {
        Long adminId = insertUser("lock-integration-admin@example.com", "ADMIN");
        Long ownerId = insertUser("lock-integration-owner@example.com", "USER");
        Instant approvedAt = Instant.parse("2026-10-10T10:00:00Z");
        Long shopId = insertApprovedShop(ownerId, adminId, approvedAt);

        shopLockService.lock(shopId, adminId, "  Vi phạm chính sách  ");

        assertEquals(ShopStatus.LOCKED.name(), value(shopId, "status", String.class));
        assertEquals("Vi phạm chính sách", value(shopId, "lock_reason", String.class));
        assertEquals(adminId, value(shopId, "locked_by", Long.class));
        assertNotNull(value(shopId, "locked_at", Timestamp.class));
        assertEquals(adminId, value(shopId, "approved_by", Long.class));
        assertEquals(Timestamp.from(approvedAt), value(shopId, "approved_at", Timestamp.class));

        shopLockService.unlock(shopId, adminId);

        assertEquals(ShopStatus.APPROVED.name(), value(shopId, "status", String.class));
        assertNull(value(shopId, "lock_reason", String.class));
        assertNull(value(shopId, "locked_by", Long.class));
        assertNull(value(shopId, "locked_at", Timestamp.class));
        assertEquals(adminId, value(shopId, "approved_by", Long.class));
        assertEquals(Timestamp.from(approvedAt), value(shopId, "approved_at", Timestamp.class));
    }

    private Long insertUser(String email, String role) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Integration User", email, "$2a$10$exampleHashForSchemaTest", role);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long insertApprovedShop(Long ownerId, Long adminId, Instant approvedAt) {
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   approved_by, approved_at,
                                   submitted_at, created_at, updated_at)
                values (?, ?, ?, ?, 'APPROVED', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, "Integration shop", "0900000000", "Test address",
                adminId, Timestamp.from(approvedAt));
        return jdbcTemplate.queryForObject("select id from shops where owner_id = ?", Long.class, ownerId);
    }

    private <T> T value(Long shopId, String column, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "select " + column + " from shops where id = ?", type, shopId);
    }
}
