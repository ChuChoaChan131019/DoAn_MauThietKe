package com.senvia.doangiuaky.identity.repository;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void lowerEmailIndexRejectsCaseInsensitiveDuplicates() {
        insertUser("CaseSensitive@example.com");

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> insertUser("casesensitive@example.com"));
    }

    @Test
    @Transactional
    void accountSearchMatchesNameAndEmailWithoutCaseSensitivity() {
        String marker = "Search" + UUID.randomUUID().toString().replace("-", "");
        User nameMatch = saveUser("Name " + marker, "name-only-" + marker + "@example.test", AccountStatus.ACTIVE, null);
        User emailMatch = saveUser("Email Match", "email-" + marker + "@example.test", AccountStatus.ACTIVE, null);

        List<User> results = userRepository
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(
                        marker.toLowerCase(), marker.toLowerCase());

        assertTrue(results.stream().anyMatch(user -> user.getId().equals(nameMatch.getId())));
        assertTrue(results.stream().anyMatch(user -> user.getId().equals(emailMatch.getId())));
    }

    @Test
    @Transactional
    void combinedStatusAndSearchQueryDoesNotIncludeMatchingAccountsWithDifferentStatus() {
        String marker = "Combined" + UUID.randomUUID().toString().replace("-", "");
        User admin = saveUser("Test Admin", "admin-" + marker + "@example.test", AccountStatus.ACTIVE, null);
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.saveAndFlush(admin);

        User activeNameMatch = saveUser("Name " + marker, "active-name-" + UUID.randomUUID() + "@example.test",
                AccountStatus.ACTIVE, null);
        User activeEmailMatch = saveUser("Email Match", "active-email-" + marker + "@example.test",
                AccountStatus.ACTIVE, null);
        User lockedMatch = saveUser("Locked " + marker, "locked-" + marker + "@example.test",
                AccountStatus.LOCKED, admin);

        List<User> activeResults = userRepository
                .findByAccountStatusAndFullNameContainingIgnoreCaseOrAccountStatusAndEmailContainingIgnoreCaseOrderByCreatedAtDesc(
                        AccountStatus.ACTIVE, marker.toLowerCase(), AccountStatus.ACTIVE, marker.toLowerCase());

        assertTrue(activeResults.stream().anyMatch(user -> user.getId().equals(activeNameMatch.getId())));
        assertTrue(activeResults.stream().anyMatch(user -> user.getId().equals(activeEmailMatch.getId())));
        assertFalse(activeResults.stream().anyMatch(user -> user.getId().equals(lockedMatch.getId())));

        List<User> lockedResults = userRepository.findAllByAccountStatusOrderByCreatedAtDesc(AccountStatus.LOCKED);
        assertTrue(lockedResults.stream().anyMatch(user -> user.getId().equals(lockedMatch.getId())));
    }

    private User saveUser(String fullName, String email, AccountStatus status, User lockedBy) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$exampleHashForSchemaTest");
        user.setRole(UserRole.USER);
        user.setAccountStatus(status);
        if (status == AccountStatus.LOCKED) {
            user.setLockReason("Repository test");
            user.setLockedBy(lockedBy);
            user.setLockedAt(Instant.parse("2026-10-15T10:00:00Z"));
        }
        return userRepository.saveAndFlush(user);
    }

    private void insertUser(String email) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "Test User",
                email,
                "$2a$10$exampleHashForSchemaTest",
                UserRole.USER.name(),
                AccountStatus.ACTIVE.name());
    }
}
