package com.senvia.doangiuaky.identity.service;

import com.senvia.doangiuaky.identity.dto.ChangePasswordForm;
import com.senvia.doangiuaky.identity.entity.User;
import com.senvia.doangiuaky.identity.repository.UserRepository;
import com.senvia.doangiuaky.identity.service.avatar.AvatarStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServicePasswordTest {

    private static final String OLD_PASSWORD = "oldPassword1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AvatarStorage avatarStorage;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private IdentityService identityService;
    private User user;
    private String oldHash;

    @BeforeEach
    void setUp() {
        identityService = new IdentityService(userRepository, passwordEncoder, avatarStorage);
        oldHash = passwordEncoder.encode(OLD_PASSWORD);
        user = new User();
        user.setPasswordHash(oldHash);
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void changePasswordStoresNewBcryptHashAndNeverPlaintext() {
        identityService.changePassword(7L, form(OLD_PASSWORD, "newPassword1", "newPassword1"));

        String newHash = user.getPasswordHash();
        assertNotEquals(oldHash, newHash);
        assertNotEquals("newPassword1", newHash);
        assertTrue(newHash.startsWith("$2"));
        assertTrue(passwordEncoder.matches("newPassword1", newHash));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, newHash));
        verify(userRepository).save(user);
    }

    @Test
    void wrongCurrentPasswordKeepsHashAndDoesNotSave() {
        InvalidPasswordException exception = assertThrows(InvalidPasswordException.class,
                () -> identityService.changePassword(7L, form("wrongPassword1", "newPassword1", "newPassword1")));

        assertEquals("Mật khẩu hiện tại không đúng.", exception.getMessage());
        assertEquals(oldHash, user.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void mismatchedConfirmationKeepsHash() {
        assertThrows(InvalidPasswordException.class,
                () -> identityService.changePassword(7L, form(OLD_PASSWORD, "newPassword1", "newPassword2")));

        assertEquals(oldHash, user.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void newPasswordMustDifferFromCurrentPassword() {
        InvalidPasswordException exception = assertThrows(InvalidPasswordException.class,
                () -> identityService.changePassword(7L, form(OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD)));

        assertEquals("Mật khẩu mới phải khác mật khẩu hiện tại.", exception.getMessage());
        assertEquals(oldHash, user.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordOver72BytesIsRejectedEvenWhenCharacterCountIsShort() {
        // 40 ký tự có dấu = 80 byte UTF-8, vượt giới hạn BCrypt 72 byte.
        String multiByte = "ệ".repeat(40);

        InvalidPasswordException exception = assertThrows(InvalidPasswordException.class,
                () -> identityService.changePassword(7L, form(OLD_PASSWORD, multiByte, multiByte)));

        assertEquals("Mật khẩu không được vượt quá 72 byte.", exception.getMessage());
        assertEquals(oldHash, user.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownAccountIsRejected() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(AccountOperationException.class,
                () -> identityService.changePassword(99L, form(OLD_PASSWORD, "newPassword1", "newPassword1")));

        verify(userRepository, never()).save(any());
    }

    private static ChangePasswordForm form(String current, String next, String confirm) {
        ChangePasswordForm form = new ChangePasswordForm();
        form.setCurrentPassword(current);
        form.setNewPassword(next);
        form.setConfirmPassword(confirm);
        return form;
    }
}
