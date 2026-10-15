package com.senvia.doangiuaky.identity.controller;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.dto.AdminAccountView;
import com.senvia.doangiuaky.identity.entity.User;
import com.senvia.doangiuaky.identity.repository.UserRepository;
import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.service.avatar.AvatarAsset;
import com.senvia.doangiuaky.identity.service.avatar.AvatarStorage;
import com.senvia.doangiuaky.identity.service.avatar.AvatarStorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityMvcTest {

    private static final String PASSWORD = "correctHorse1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @MockitoBean
    private AvatarStorage avatarStorage;

    private final String testPrefix = "mvc-" + UUID.randomUUID();

    @AfterEach
    void removeTestUsers() {
        jdbcTemplate.update("""
                update users
                set account_status = 'ACTIVE', lock_reason = null, locked_by = null, locked_at = null
                where email like ?
                """, testPrefix + "-%");
        jdbcTemplate.update("delete from users where email like ?", testPrefix + "-%");
    }

    @Test
    void registrationValidatesFieldsNormalizesEmailAndStoresBcrypt() throws Exception {
        String email = testPrefix + "-buyer@example.test";

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "  MVC Buyer  ")
                        .param("email", "  " + email.toUpperCase() + "  ")
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        User savedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertEquals("MVC Buyer", savedUser.getFullName());
        assertEquals(email, savedUser.getEmail());
        assertTrue(passwordEncoder.matches(PASSWORD, savedUser.getPasswordHash()));
        assertEquals(UserRole.USER, savedUser.getRole());
        assertEquals(AccountStatus.ACTIVE, savedUser.getAccountStatus());
    }

    @Test
    void registrationRejectsInvalidEmailDuplicateAndMismatchedPasswords() throws Exception {
        String email = testPrefix + "-duplicate@example.test";
        createUser(email, UserRole.USER, AccountStatus.ACTIVE);

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "MVC Buyer")
                        .param("email", email.toUpperCase())
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/register"))
                .andExpect(model().attributeHasFieldErrors("registrationForm", "email"));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "MVC Buyer")
                        .param("email", "not-an-email")
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrationForm", "email"));

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "MVC Buyer")
                        .param("email", testPrefix + "-mismatch@example.test")
                        .param("password", PASSWORD)
                        .param("confirmPassword", "differentPassword1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("registrationForm", "confirmPassword"));
    }

    @Test
    void registrationRejectsPasswordThatExceedsBcryptUtf8ByteLimit() throws Exception {
        String email = testPrefix + "-unicode@example.test";
        String password = "é".repeat(37);

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "MVC Buyer")
                        .param("email", email)
                        .param("password", password)
                        .param("confirmPassword", password))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/register"))
                .andExpect(model().attributeHasFieldErrors("registrationForm", "password"));

        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByEmailIgnoreCase(email).isEmpty());
    }

    @Test
    void loginAndLogoutManageAuthenticatedSession() throws Exception {
        String email = testPrefix + "-login@example.test";
        createUser(email, UserRole.USER, AccountStatus.ACTIVE);

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", "wrongPassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", email.toUpperCase())
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(email))
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(get("/account/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"));

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        mockMvc.perform(get("/account/profile").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertTrue(result.getResponse().getRedirectedUrl().startsWith("/login")));
    }

    @Test
    void lockedAccountCannotAuthenticateAndUserCannotAccessAdminOrPurchaseAdminCannotPurchase()
            throws Exception {
        User admin = createUser(testPrefix + "-admin@example.test", UserRole.ADMIN, AccountStatus.ACTIVE);
        User lockedUser = new User();
        lockedUser.setFullName("Locked MVC User");
        lockedUser.setEmail(testPrefix + "-locked@example.test");
        lockedUser.setPasswordHash(passwordEncoder.encode(PASSWORD));
        lockedUser.setRole(UserRole.USER);
        lockedUser.setAccountStatus(AccountStatus.LOCKED);
        lockedUser.setLockReason("Policy violation");
        lockedUser.setLockedBy(admin);
        lockedUser.setLockedAt(Instant.now());
        lockedUser = userRepository.saveAndFlush(lockedUser);

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", lockedUser.getEmail())
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        MockHttpSession userSession = login(testPrefix + "-user@example.test", UserRole.USER);
        mockMvc.perform(get("/identity/admin/accounts").session(userSession))
                .andExpect(status().isForbidden());

        MockHttpSession adminSession = login(admin.getEmail(), UserRole.ADMIN);
        mockMvc.perform(get("/cart").session(adminSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/identity/admin/accounts").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/admin/accounts"));
    }

    @Test
    void adminCanSearchByEmailFilterByStatusAndOpenAccountDetails() throws Exception {
        User admin = createUser(testPrefix + "-admin@example.test", UserRole.ADMIN, AccountStatus.ACTIVE);
        User matchingUser = createUser(testPrefix + "-searchmatch@example.test", UserRole.USER, AccountStatus.ACTIVE);
        User lockedUser = createUser(testPrefix + "-locked@example.test", UserRole.USER, AccountStatus.ACTIVE);
        lockedUser.setAccountStatus(AccountStatus.LOCKED);
        lockedUser.setLockReason("Terms violation");
        lockedUser.setLockedBy(admin);
        lockedUser.setLockedAt(Instant.now());
        lockedUser = userRepository.saveAndFlush(lockedUser);
        MockHttpSession session = login(admin.getEmail(), UserRole.ADMIN);

        MvcResult searchResult = mockMvc.perform(get("/identity/admin/accounts")
                        .session(session)
                        .param("query", "SEARCHMATCH"))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/admin/accounts"))
                .andReturn();
        List<?> searchResults = (List<?>) searchResult.getModelAndView().getModel().get("accounts");
        assertEquals(1, searchResults.size());
        assertEquals(matchingUser.getId(), ((AdminAccountView) searchResults.get(0)).id());

        MvcResult filteredResult = mockMvc.perform(get("/identity/admin/accounts")
                        .session(session)
                        .param("status", AccountStatus.LOCKED.name()))
                .andExpect(status().isOk())
                .andReturn();
        List<?> filteredResults = (List<?>) filteredResult.getModelAndView().getModel().get("accounts");
        assertEquals(1, filteredResults.size());
        assertEquals(lockedUser.getId(), ((AdminAccountView) filteredResults.get(0)).id());

        MvcResult detailResult = mockMvc.perform(get("/identity/admin/accounts/{id}", lockedUser.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/admin/accounts"))
                .andReturn();
        AdminAccountView detail = (AdminAccountView) detailResult.getModelAndView()
                .getModel().get("selectedAccount");
        assertEquals(lockedUser.getId(), detail.id());
        assertEquals("Terms violation", detail.lockReason());
        assertEquals("MVC User", detail.lockedByName());
        assertNotNull(detail.lockedAt());
    }

    @Test
    void profileAndPasswordChangesUseAuthenticatedAccountRatherThanSubmittedUserId() throws Exception {
        String email = testPrefix + "-owner@example.test";
        String otherEmail = testPrefix + "-other@example.test";
        User owner = createUser(email, UserRole.USER, AccountStatus.ACTIVE);
        User other = createUser(otherEmail, UserRole.USER, AccountStatus.ACTIVE);
        owner.setAvatarUrl("https://images.example.test/old.png");
        owner.setAvatarPublicId("old-avatar");
        owner = userRepository.saveAndFlush(owner);
        MockHttpSession session = login(email, UserRole.USER);

        mockMvc.perform(get("/account/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"))
                .andExpect(model().attribute("profileForm",
                        org.hamcrest.Matchers.hasProperty("fullName", org.hamcrest.Matchers.is("MVC User"))));

        mockMvc.perform(post("/account/profile")
                        .session(session)
                        .with(csrf())
                        .param("userId", other.getId().toString())
                        .param("fullName", "Updated Owner")
                        .param("phone", "0123456789")
                        .param("address", "Updated address")
                        .param("role", "ADMIN")
                        .param("accountStatus", "LOCKED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile"));

        User updatedOwner = userRepository.findById(owner.getId()).orElseThrow();
        assertEquals("Updated Owner", updatedOwner.getFullName());
        assertEquals(UserRole.USER, updatedOwner.getRole());
        assertEquals(AccountStatus.ACTIVE, updatedOwner.getAccountStatus());
        assertEquals("MVC User", userRepository.findById(other.getId()).orElseThrow().getFullName());

        mockMvc.perform(multipart("/account/profile")
                        .session(session)
                        .file(new MockMultipartFile("avatar", "avatar.png", "image/png", new byte[] {1}))
                        .param("fullName", " ")
                        .param("phone", "0123456789")
                        .param("address", "Invalid update")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"))
                .andExpect(model().attributeHasFieldErrors("profileForm", "fullName"));
        assertEquals("Updated Owner", userRepository.findById(owner.getId()).orElseThrow().getFullName());
        verify(avatarStorage, org.mockito.Mockito.never()).upload(eq(owner.getId()), any());

        mockMvc.perform(post("/account/change-password")
                        .session(session)
                        .with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "newCorrectHorse1")
                        .param("confirmPassword", "differentPassword1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordForm", "confirmPassword"));
        assertTrue(passwordEncoder.matches(PASSWORD, userRepository.findById(owner.getId())
                .orElseThrow().getPasswordHash()));

        mockMvc.perform(post("/account/change-password")
                        .session(session)
                        .with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"));
        assertTrue(passwordEncoder.matches(PASSWORD, userRepository.findById(owner.getId())
                .orElseThrow().getPasswordHash()));

        mockMvc.perform(post("/account/change-password")
                        .session(session)
                        .with(csrf())
                        .param("currentPassword", "wrongPassword")
                        .param("newPassword", "newCorrectHorse1")
                        .param("confirmPassword", "newCorrectHorse1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("passwordError"));
        assertTrue(passwordEncoder.matches(PASSWORD, userRepository.findById(owner.getId())
                .orElseThrow().getPasswordHash()));

        mockMvc.perform(post("/account/change-password")
                        .session(session)
                        .with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "newCorrectHorse1")
                        .param("confirmPassword", "newCorrectHorse1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/change-password"));
        assertTrue(passwordEncoder.matches("newCorrectHorse1", userRepository.findById(owner.getId())
                .orElseThrow().getPasswordHash()));
    }

    @Test
    void profileAvatarReplacementDeletesPreviousAssetAfterSuccessfulUpdate() throws Exception {
        User owner = createUser(testPrefix + "-avatar-owner@example.test", UserRole.USER, AccountStatus.ACTIVE);
        owner.setAvatarUrl("https://images.example.test/old.png");
        owner.setAvatarPublicId("old-avatar");
        owner = userRepository.saveAndFlush(owner);
        MockHttpSession session = login(owner.getEmail(), UserRole.USER);
        when(avatarStorage.upload(eq(owner.getId()), any()))
                .thenReturn(new AvatarAsset("https://images.example.test/new.png", "new-avatar"));
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "new.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/account/profile")
                        .file(avatar)
                        .session(session)
                        .with(csrf())
                        .param("fullName", "Updated Avatar Owner")
                        .param("phone", "0123456789")
                        .param("address", "Updated address"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile"));

        User updatedOwner = userRepository.findById(owner.getId()).orElseThrow();
        assertEquals("https://images.example.test/new.png", updatedOwner.getAvatarUrl());
        assertEquals("new-avatar", updatedOwner.getAvatarPublicId());
        verify(avatarStorage).delete("old-avatar");
    }

    @Test
    void profileAvatarStorageFailureShowsErrorAndDoesNotPersistProfileChanges() throws Exception {
        User owner = createUser(testPrefix + "-avatar-error@example.test", UserRole.USER, AccountStatus.ACTIVE);
        owner.setFullName("Original Owner");
        owner.setAvatarUrl("https://images.example.test/old.png");
        owner.setAvatarPublicId("old-avatar");
        owner = userRepository.saveAndFlush(owner);
        MockHttpSession session = login(owner.getEmail(), UserRole.USER);
        doThrow(new AvatarStorageException("Upload service unavailable"))
                .when(avatarStorage).upload(eq(owner.getId()), any());
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "new.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/account/profile")
                        .file(avatar)
                        .session(session)
                        .with(csrf())
                        .param("fullName", "Should Not Persist")
                        .param("phone", "0123456789")
                        .param("address", "Should not persist"))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"))
                .andExpect(model().attribute("avatarError", "Upload service unavailable"));

        User unchangedOwner = userRepository.findById(owner.getId()).orElseThrow();
        assertEquals("Original Owner", unchangedOwner.getFullName());
        assertEquals("https://images.example.test/old.png", unchangedOwner.getAvatarUrl());
        assertEquals("old-avatar", unchangedOwner.getAvatarPublicId());
        verify(avatarStorage, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void passwordChangeKeepsCurrentSessionAndExpiresOtherSessionsOfSameUserOnly() throws Exception {
        User owner = createUser(testPrefix + "-password-owner@example.test", UserRole.USER, AccountStatus.ACTIVE);
        User unrelated = createUser(testPrefix + "-password-other@example.test", UserRole.USER, AccountStatus.ACTIVE);
        MockHttpSession currentSession = login(owner.getEmail(), UserRole.USER);
        MockHttpSession otherOwnerSession = login(owner.getEmail(), UserRole.USER);
        MockHttpSession unrelatedSession = login(unrelated.getEmail(), UserRole.USER);
        SessionInformation currentInfo = sessionRegistry.getSessionInformation(currentSession.getId());
        SessionInformation otherOwnerInfo = sessionRegistry.getSessionInformation(otherOwnerSession.getId());
        SessionInformation unrelatedInfo = sessionRegistry.getSessionInformation(unrelatedSession.getId());
        assertNotNull(currentInfo);
        assertNotNull(otherOwnerInfo);
        assertNotNull(unrelatedInfo);

        mockMvc.perform(post("/account/change-password")
                        .session(currentSession)
                        .with(csrf())
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "newCorrectHorse1")
                        .param("confirmPassword", "newCorrectHorse1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/change-password"));

        assertTrue(passwordEncoder.matches("newCorrectHorse1",
                userRepository.findById(owner.getId()).orElseThrow().getPasswordHash()));
        assertTrue(!currentInfo.isExpired());
        assertTrue(otherOwnerInfo.isExpired());
        assertTrue(!unrelatedInfo.isExpired());
        mockMvc.perform(get("/account/profile").session(currentSession))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"));

        MvcResult expiredSessionRequest = mockMvc.perform(get("/account/profile").session(otherOwnerSession))
                .andReturn();
        assertNull(expiredSessionRequest.getHandler());
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", owner.getEmail())
                        .param("password", "newCorrectHorse1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(owner.getEmail()));
    }

    @Test
    void adminCanLockAndUnlockUserAndLockingExpiresItsExistingSession() throws Exception {
        assertTrue(filterChainProxy.getFilters("/account/profile").stream()
                .anyMatch(ConcurrentSessionFilter.class::isInstance));
        User admin = createUser(testPrefix + "-admin@example.test", UserRole.ADMIN, AccountStatus.ACTIVE);
        User target = createUser(testPrefix + "-target@example.test", UserRole.USER, AccountStatus.ACTIVE);
        MockHttpSession targetSession = login(target.getEmail(), UserRole.USER);
        MockHttpSession adminSession = login(admin.getEmail(), UserRole.ADMIN);
        SessionInformation registeredSession = sessionRegistry.getSessionInformation(targetSession.getId());
        assertNotNull(registeredSession);
        assertEquals(target.getId(), ((IdentityPrincipal) registeredSession.getPrincipal()).getUserId());

        mockMvc.perform(post("/identity/admin/accounts/{id}/lock", target.getId())
                        .session(adminSession)
                        .with(csrf())
                        .param("reason", "  Terms violation  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/identity/admin/accounts/" + target.getId()))
                .andExpect(flash().attribute("success", "Tài khoản đã được khóa."));
        assertTrue(registeredSession.isExpired());

        User lockedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertEquals(AccountStatus.LOCKED, lockedTarget.getAccountStatus());
        assertEquals("Terms violation", lockedTarget.getLockReason());
        assertEquals(admin.getId(), lockedTarget.getLockedBy().getId());
        assertNotNull(lockedTarget.getLockedAt());

        MvcResult expiredRequest = mockMvc.perform(get("/account/profile").session(targetSession))
                .andReturn();
        assertNull(expiredRequest.getRequest().getSession(false));
        assertNull(expiredRequest.getHandler());

        mockMvc.perform(post("/identity/admin/accounts/{id}/unlock", target.getId())
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Tài khoản đã được mở khóa."));
        User unlockedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, unlockedTarget.getAccountStatus());
        org.junit.jupiter.api.Assertions.assertNull(unlockedTarget.getLockReason());
        org.junit.jupiter.api.Assertions.assertNull(unlockedTarget.getLockedBy());
        org.junit.jupiter.api.Assertions.assertNull(unlockedTarget.getLockedAt());
    }

    @Test
    void adminCannotLockSelfOrAnotherAdminAndLockReasonIsRequired() throws Exception {
        User admin = createUser(testPrefix + "-admin@example.test", UserRole.ADMIN, AccountStatus.ACTIVE);
        User otherAdmin = createUser(testPrefix + "-other-admin@example.test", UserRole.ADMIN, AccountStatus.ACTIVE);
        MockHttpSession session = login(admin.getEmail(), UserRole.ADMIN);

        mockMvc.perform(post("/identity/admin/accounts/{id}/lock", admin.getId())
                        .session(session)
                        .with(csrf())
                        .param("reason", "Self lock"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        mockMvc.perform(post("/identity/admin/accounts/{id}/lock", otherAdmin.getId())
                        .session(session)
                        .with(csrf())
                        .param("reason", "Admin lock"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        User target = createUser(testPrefix + "-user@example.test", UserRole.USER, AccountStatus.ACTIVE);
        mockMvc.perform(post("/identity/admin/accounts/{id}/lock", target.getId())
                        .session(session)
                        .with(csrf())
                        .param("reason", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        assertEquals(AccountStatus.ACTIVE, userRepository.findById(target.getId()).orElseThrow().getAccountStatus());
    }

    private User createUser(String email, UserRole role, AccountStatus status) {
        User user = new User();
        user.setFullName("MVC User");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setAccountStatus(status);
        return userRepository.saveAndFlush(user);
    }

    private MockHttpSession login(String email, UserRole role) throws Exception {
        if (userRepository.findByEmailIgnoreCase(email).isEmpty()) {
            createUser(email, role, AccountStatus.ACTIVE);
        }
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(email))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
