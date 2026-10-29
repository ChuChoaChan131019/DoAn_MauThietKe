package com.senvia.doangiuaky.identity.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.dto.ProfileForm;
import com.senvia.doangiuaky.identity.entity.User;
import com.senvia.doangiuaky.identity.repository.UserRepository;
import com.senvia.doangiuaky.identity.service.avatar.AvatarAsset;
import com.senvia.doangiuaky.identity.service.avatar.AvatarStorage;
import com.senvia.doangiuaky.identity.service.avatar.AvatarStorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceProfileTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AvatarStorage avatarStorage;

    @InjectMocks
    private IdentityService identityService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setFullName("Old Name");
        user.setPasswordHash("$2a$existing-hash");
        user.setRole(UserRole.USER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setAvatarUrl("https://res.example.test/old.png");
        user.setAvatarPublicId("old-public-id");
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void getProfileReturnsOnlyEditableFields() {
        user.setPhone("0900000000");
        user.setAddress("Ha Noi");

        ProfileForm form = identityService.getProfile(7L);

        assertEquals("Old Name", form.getFullName());
        assertEquals("0900000000", form.getPhone());
        assertEquals("Ha Noi", form.getAddress());
    }

    @Test
    void updateProfileTrimsFieldsAndNeverChangesRoleStatusOrPassword() {
        ProfileForm form = new ProfileForm();
        form.setFullName("  New Name  ");
        form.setPhone("   ");
        form.setAddress("  Ha Noi  ");

        identityService.updateProfile(7L, form, null);

        assertEquals("New Name", user.getFullName());
        assertNull(user.getPhone());
        assertEquals("Ha Noi", user.getAddress());
        assertEquals(UserRole.USER, user.getRole());
        assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        assertEquals("$2a$existing-hash", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(avatarStorage, never()).upload(any(), any());
    }

    @Test
    void emptyAvatarFileKeepsCurrentAvatar() {
        ProfileForm form = new ProfileForm();
        form.setFullName("New Name");

        identityService.updateProfile(7L, form, new MockMultipartFile("avatar", new byte[0]));

        assertEquals("old-public-id", user.getAvatarPublicId());
        verify(avatarStorage, never()).upload(any(), any());
    }

    @Test
    void replacingAvatarStoresNewAssetAndDeletesOldOneOnlyAfterCommit() {
        MockMultipartFile file = new MockMultipartFile("avatar", "a.png", "image/png", new byte[] {1});
        when(avatarStorage.upload(7L, file))
                .thenReturn(new AvatarAsset("https://res.example.test/new.png", "new-public-id"));
        ProfileForm form = new ProfileForm();
        form.setFullName("New Name");

        identityService.updateProfile(7L, form, file);

        assertEquals("https://res.example.test/new.png", user.getAvatarUrl());
        assertEquals("new-public-id", user.getAvatarPublicId());
        verify(userRepository).saveAndFlush(user);
        verify(avatarStorage, never()).delete(any());

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(avatarStorage).delete("old-public-id");
        verify(avatarStorage, never()).delete("new-public-id");
    }

    @Test
    void rollbackDeletesNewlyUploadedAvatarAndKeepsOldOne() {
        MockMultipartFile file = new MockMultipartFile("avatar", "a.png", "image/png", new byte[] {1});
        when(avatarStorage.upload(7L, file))
                .thenReturn(new AvatarAsset("https://res.example.test/new.png", "new-public-id"));
        ProfileForm form = new ProfileForm();
        form.setFullName("New Name");

        identityService.updateProfile(7L, form, file);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(avatarStorage).delete("new-public-id");
        verify(avatarStorage, never()).delete("old-public-id");
    }

    @Test
    void storageUploadFailureLeavesProfileUnsaved() {
        MockMultipartFile file = new MockMultipartFile("avatar", "a.png", "image/png", new byte[] {1});
        when(avatarStorage.upload(7L, file)).thenThrow(new AvatarStorageException("Cloudinary lỗi."));
        ProfileForm form = new ProfileForm();
        form.setFullName("New Name");

        assertThrows(AvatarStorageException.class, () -> identityService.updateProfile(7L, form, file));

        assertEquals("old-public-id", user.getAvatarPublicId());
        verify(userRepository, never()).saveAndFlush(any());
        verify(avatarStorage, never()).delete(any());
    }

    @Test
    void databaseFailureCleansUpUploadedAvatar() {
        MockMultipartFile file = new MockMultipartFile("avatar", "a.png", "image/png", new byte[] {1});
        when(avatarStorage.upload(7L, file))
                .thenReturn(new AvatarAsset("https://res.example.test/new.png", "new-public-id"));
        when(userRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("db"));
        ProfileForm form = new ProfileForm();
        form.setFullName("New Name");

        assertThrows(DataIntegrityViolationException.class, () -> identityService.updateProfile(7L, form, file));

        verify(avatarStorage).delete("new-public-id");
        verify(avatarStorage, never()).delete("old-public-id");
    }

    @Test
    void unknownAccountIsRejected() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        ProfileForm form = new ProfileForm();
        form.setFullName("New Name");

        assertThrows(AccountOperationException.class, () -> identityService.updateProfile(99L, form, null));

        verify(userRepository, never()).save(any());
    }
}
