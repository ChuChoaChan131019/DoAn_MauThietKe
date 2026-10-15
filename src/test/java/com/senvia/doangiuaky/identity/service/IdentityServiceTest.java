package com.senvia.doangiuaky.identity.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.dto.ChangePasswordForm;
import com.senvia.doangiuaky.identity.dto.RegistrationForm;
import com.senvia.doangiuaky.identity.entity.User;
import com.senvia.doangiuaky.identity.repository.UserRepository;
import com.senvia.doangiuaky.identity.service.avatar.AvatarStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private AvatarStorage avatarStorage;

    @InjectMocks
    private IdentityService identityService;

    @Test
    void registerTrimsAndLowercasesEmailAndStoresBcryptHash() {
        RegistrationForm form = new RegistrationForm();
        form.setFullName("  Nguyen Van A  ");
        form.setEmail("  Buyer@Example.com  ");
        form.setPassword("correctHorse1");
        form.setConfirmPassword("correctHorse1");
        when(userRepository.existsByEmailIgnoreCase("buyer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correctHorse1")).thenReturn("$2a$bcrypt-hash");

        identityService.register(form);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(savedUser.capture());
        assertEquals("Nguyen Van A", savedUser.getValue().getFullName());
        assertEquals("buyer@example.com", savedUser.getValue().getEmail());
        assertEquals("$2a$bcrypt-hash", savedUser.getValue().getPasswordHash());
        assertEquals(UserRole.USER, savedUser.getValue().getRole());
        assertEquals(AccountStatus.ACTIVE, savedUser.getValue().getAccountStatus());
    }

    @Test
    void registerRejectsDuplicateEmailBeforeEncodingPassword() {
        RegistrationForm form = new RegistrationForm();
        form.setFullName("Nguyen Van A");
        form.setEmail("BUYER@example.com");
        form.setPassword("correctHorse1");
        form.setConfirmPassword("correctHorse1");
        when(userRepository.existsByEmailIgnoreCase("buyer@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> identityService.register(form));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerRejectsUnicodePasswordOverBcryptByteLimitBeforeEncoding() {
        RegistrationForm form = new RegistrationForm();
        form.setFullName("Nguyen Van A");
        form.setEmail("buyer@example.com");
        form.setPassword("é".repeat(37));
        form.setConfirmPassword(form.getPassword());
        when(userRepository.existsByEmailIgnoreCase("buyer@example.com")).thenReturn(false);

        assertThrows(InvalidPasswordException.class, () -> identityService.register(form));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        User user = new User();
        user.setPasswordHash("$2a$existing-hash");
        when(userRepository.findById(19L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "$2a$existing-hash")).thenReturn(false);
        ChangePasswordForm form = new ChangePasswordForm();
        form.setCurrentPassword("wrongPassword");
        form.setNewPassword("newPassword1");
        form.setConfirmPassword("newPassword1");

        assertThrows(InvalidPasswordException.class, () -> identityService.changePassword(19L, form));

        verify(userRepository, never()).save(any());
    }
}
