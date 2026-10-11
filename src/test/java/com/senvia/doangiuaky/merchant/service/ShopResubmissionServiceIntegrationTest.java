package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.api.ShopRequestedEvent;
import com.senvia.doangiuaky.merchant.dto.ShopResubmissionForm;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@RecordApplicationEvents
class ShopResubmissionServiceIntegrationTest {

    @Autowired
    private ShopResubmissionService shopResubmissionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    @Transactional
    void resubmitsRejectedShopAndPersistsUpdatedDataOnTheSameRow() {
        Long ownerId = insertUser("resubmission-owner@example.com", "USER");
        Long adminId = insertUser("resubmission-admin@example.com", "ADMIN");
        Long shopId = insertRejectedShop(ownerId, adminId, "Rejected shop");

        ShopResubmissionForm form = new ShopResubmissionForm();
        form.setShopName("  Corrected shop  ");
        form.setDescription("  Corrected description  ");
        form.setPhone(" 0900000012 ");
        form.setAddress(" Corrected address ");

        shopResubmissionService.resubmit(ownerId, form);
        shopRepository.flush();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select id, status, shop_name, description, phone, address, rejection_reason, approved_by, approved_at "
                        + "from shops where id = ?", shopId);
        assertEquals(shopId, ((Number) row.get("ID")).longValue());
        assertEquals("PENDING", row.get("STATUS"));
        assertEquals("Corrected shop", row.get("SHOP_NAME"));
        assertEquals("Corrected description", row.get("DESCRIPTION"));
        assertEquals("0900000012", row.get("PHONE"));
        assertEquals("Corrected address", row.get("ADDRESS"));
        assertNull(row.get("REJECTION_REASON"));
        assertNull(row.get("APPROVED_BY"));
        assertNull(row.get("APPROVED_AT"));

        ShopRequestedEvent event = applicationEvents.stream(ShopRequestedEvent.class)
                .findFirst()
                .orElseThrow();
        assertNotNull(event.eventId());
        assertEquals(shopId, event.shopId());
        assertEquals(ownerId, event.ownerId());
        assertEquals("Corrected shop", event.shopName());
        assertNotNull(event.submittedAt());
    }

    private Long insertUser(String email, String role) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Test " + role, email, "$2a$10$exampleHashForSchemaTest", role);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long insertRejectedShop(Long ownerId, Long adminId, String shopName) {
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, description, phone, address, status,
                                   rejection_reason, approved_by, submitted_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'REJECTED', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName, "Old description", "0900000000", "Old address",
                "Thiếu thông tin", adminId);
        return jdbcTemplate.queryForObject("select id from shops where owner_id = ?", Long.class, ownerId);
    }
}
