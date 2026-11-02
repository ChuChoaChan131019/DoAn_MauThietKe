package com.senvia.doangiuaky.identity.controller;

import com.senvia.doangiuaky.identity.dto.ProfileForm;
import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.identity.service.IdentityService;
import com.senvia.doangiuaky.identity.service.avatar.AvatarStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountProfileControllerTest {

    private static final String AVATAR_URL = "https://res.example.test/avatar.png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityService identityService;

    private IdentityPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = principal(7L);
        ProfileForm current = new ProfileForm();
        current.setFullName("Nguyen Van A");
        when(identityService.getProfile(7L)).thenReturn(current);
        when(identityService.getAvatarUrl(7L)).thenReturn(AVATAR_URL);
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/account/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(identityService, never()).getProfile(any());
    }

    @Test
    void ownerViewsOnlyOwnProfile() throws Exception {
        mockMvc.perform(get("/account/profile").param("userId", "99").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"))
                .andExpect(model().attribute("avatarUrl", AVATAR_URL))
                .andExpect(content().string(containsString("Nguyen Van A")));

        verify(identityService).getProfile(7L);
        verify(identityService, never()).getProfile(99L);
    }

    @Test
    void ownerUpdatesProfileWithoutAvatar() throws Exception {
        mockMvc.perform(multipart("/account/profile")
                        .param("fullName", "Nguyen Van B")
                        .param("phone", "0900000000")
                        .param("address", "Ha Noi")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile"))
                .andExpect(flash().attribute("success", "Thông tin hồ sơ đã được cập nhật."));

        ArgumentCaptor<ProfileForm> form = ArgumentCaptor.forClass(ProfileForm.class);
        verify(identityService).updateProfile(eq(7L), form.capture(), any());
        assertEquals("Nguyen Van B", form.getValue().getFullName());
        assertEquals("0900000000", form.getValue().getPhone());
        assertEquals("Ha Noi", form.getValue().getAddress());
    }

    @Test
    void forgedUserIdRoleAndStatusParametersAreIgnored() throws Exception {
        mockMvc.perform(multipart("/account/profile")
                        .param("fullName", "Nguyen Van B")
                        .param("userId", "99")
                        .param("id", "99")
                        .param("role", "ADMIN")
                        .param("accountStatus", "LOCKED")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(identityService).updateProfile(eq(7L), any(ProfileForm.class), any());
        verify(identityService, never()).updateProfile(eq(99L), any(), any());
    }

    @Test
    void profileUpdateRequiresCsrfToken() throws Exception {
        mockMvc.perform(multipart("/account/profile")
                        .param("fullName", "Nguyen Van B")
                        .with(user(principal)))
                .andExpect(status().isForbidden());

        verify(identityService, never()).updateProfile(any(), any(), any());
    }

    @Test
    void blankFullNameIsRejectedWithoutSaving() throws Exception {
        mockMvc.perform(multipart("/account/profile")
                        .param("fullName", "   ")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"))
                .andExpect(model().attributeHasFieldErrors("profileForm", "fullName"))
                .andExpect(model().attribute("avatarUrl", AVATAR_URL));

        verify(identityService, never()).updateProfile(any(), any(), any());
    }

    @Test
    void overlongPhoneAndAddressAreRejectedWithoutSaving() throws Exception {
        mockMvc.perform(multipart("/account/profile")
                        .param("fullName", "Nguyen Van B")
                        .param("phone", "1".repeat(21))
                        .param("address", "a".repeat(2001))
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"))
                .andExpect(model().attributeHasFieldErrors("profileForm", "phone", "address"));

        verify(identityService, never()).updateProfile(any(), any(), any());
    }

    @Test
    void ownerUploadsAvatarWithProfileUpdate() throws Exception {
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/account/profile")
                        .file(avatar)
                        .param("fullName", "Nguyen Van B")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile"));

        ArgumentCaptor<MultipartFile> uploaded = ArgumentCaptor.forClass(MultipartFile.class);
        verify(identityService).updateProfile(eq(7L), any(ProfileForm.class), uploaded.capture());
        assertEquals("avatar.png", uploaded.getValue().getOriginalFilename());
        assertEquals(3, uploaded.getValue().getSize());
    }

    @Test
    void profileUpdateWithoutAvatarPassesNoFile() throws Exception {
        mockMvc.perform(multipart("/account/profile")
                        .param("fullName", "Nguyen Van B")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<MultipartFile> uploaded = ArgumentCaptor.forClass(MultipartFile.class);
        verify(identityService).updateProfile(eq(7L), any(ProfileForm.class), uploaded.capture());
        assertNull(uploaded.getValue());
    }

    @Test
    void avatarStorageFailureShowsErrorAndKeepsCurrentAvatar() throws Exception {
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", new byte[] {1, 2, 3});
        doThrow(new AvatarStorageException("Không thể tải ảnh đại diện lên Cloudinary."))
                .when(identityService).updateProfile(eq(7L), any(ProfileForm.class), any());

        mockMvc.perform(multipart("/account/profile")
                        .file(avatar)
                        .param("fullName", "Nguyen Van B")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/profile"))
                .andExpect(model().attribute("avatarError", "Không thể tải ảnh đại diện lên Cloudinary."))
                .andExpect(model().attribute("avatarUrl", AVATAR_URL))
                .andExpect(content().string(containsString("Không thể tải ảnh đại diện lên Cloudinary.")));
    }

    private static IdentityPrincipal principal(Long userId) {
        IdentityPrincipal principal = mock(IdentityPrincipal.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getUsername()).thenReturn("user" + userId + "@example.test");
        when(principal.getPassword()).thenReturn("password");
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getAuthorities())
                .thenAnswer(ignored -> List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return principal;
    }
}
