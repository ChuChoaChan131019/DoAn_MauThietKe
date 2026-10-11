package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.ShopRequestedEvent;
import com.senvia.doangiuaky.merchant.dto.ShopResubmissionForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopResubmissionServiceTest {

    private final ShopRepository shopRepository = mock(ShopRepository.class);
    private final IdentityApi identityApi = mock(IdentityApi.class);
    private final MerchantImageStorage imageStorage = mock(MerchantImageStorage.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ShopResubmissionService service = new ShopResubmissionService(
            shopRepository, identityApi, imageStorage, eventPublisher);

    @BeforeEach
    void setUpEligibleOwner() {
        when(identityApi.findUser(12L)).thenReturn(Optional.of(
                new UserSummary(12L, "Owner", UserRole.USER, AccountStatus.ACTIVE)));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void resubmitsRejectedShopOnTheSameRecordAndClearsRejectionMetadata() {
        Shop shop = rejectedShop();
        when(shopRepository.findByOwnerIdForUpdate(12L)).thenReturn(Optional.of(shop));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shop saved = service.resubmit(12L, validFormWithoutLogo());

        assertEquals(ShopStatus.PENDING, saved.getStatus());
        assertEquals(12L, saved.getOwnerId());
        assertEquals("Updated shop", saved.getShopName());
        assertEquals("Updated description", saved.getDescription());
        assertEquals("0900000012", saved.getPhone());
        assertEquals("Updated address", saved.getAddress());
        assertEquals("https://images.example/old-logo.png", saved.getLogoUrl());
        assertEquals("old-logo", saved.getLogoPublicId());
        assertNull(saved.getRejectionReason());
        assertNull(saved.getApprovedById());
        assertNull(saved.getApprovedAt());
        assertNotNull(saved.getSubmittedAt());
        verify(imageStorage, never()).uploadShopLogo(any(), any());
        verify(imageStorage, never()).delete(any());

        verify(eventPublisher).publishEvent(any(ShopRequestedEvent.class));
    }

    @Test
    void replacesLogoAndDeletesPreviousLogoAfterSuccessfulSave() {
        Shop shop = rejectedShop();
        ShopResubmissionForm form = validFormWithoutLogo();
        form.setLogo(new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1}));
        when(shopRepository.findByOwnerIdForUpdate(12L)).thenReturn(Optional.of(shop));
        when(imageStorage.uploadShopLogo(eq(12L), any())).thenReturn(
                new MerchantImageAsset("https://images.example/new-logo.png", "new-logo"));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.resubmit(12L, form);

        assertEquals("https://images.example/new-logo.png", shop.getLogoUrl());
        assertEquals("new-logo", shop.getLogoPublicId());
        verify(imageStorage).delete("old-logo");
    }

    @Test
    void deletesNewLogoWhenDatabaseSaveFailsAndKeepsPreviousLogo() {
        Shop shop = rejectedShop();
        ShopResubmissionForm form = validFormWithoutLogo();
        form.setLogo(new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1}));
        when(shopRepository.findByOwnerIdForUpdate(12L)).thenReturn(Optional.of(shop));
        when(imageStorage.uploadShopLogo(eq(12L), any())).thenReturn(
                new MerchantImageAsset("https://images.example/new-logo.png", "new-logo"));
        when(shopRepository.saveAndFlush(any(Shop.class)))
                .thenThrow(new DataIntegrityViolationException("database unavailable"));

        assertThrows(DataIntegrityViolationException.class, () -> service.resubmit(12L, form));

        verify(imageStorage).delete("new-logo");
        verify(imageStorage, never()).delete("old-logo");
    }

    @Test
    void deletesNewLogoWhenTransactionRollsBackAfterFlush() {
        Shop shop = rejectedShop();
        ShopResubmissionForm form = validFormWithoutLogo();
        form.setLogo(new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1}));
        when(shopRepository.findByOwnerIdForUpdate(12L)).thenReturn(Optional.of(shop));
        when(imageStorage.uploadShopLogo(eq(12L), any())).thenReturn(
                new MerchantImageAsset("https://images.example/new-logo.png", "new-logo"));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.resubmit(12L, form);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(imageStorage).delete("new-logo");
        verify(imageStorage, never()).delete("old-logo");
    }

    @Test
    void rejectsPendingShopBeforeUploadingOrSaving() {
        Shop shop = Shop.createPending(12L, "Pending shop", "Description",
                "https://images.example/logo.png", "logo", "0900000000", "Address");
        when(shopRepository.findByOwnerIdForUpdate(12L)).thenReturn(Optional.of(shop));

        assertThrows(ShopResubmissionException.class,
                () -> service.resubmit(12L, validFormWithoutLogo()));

        verify(imageStorage, never()).uploadShopLogo(any(), any());
        verify(shopRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsInactiveOrUnknownOwnerBeforeLockingShop() {
        when(identityApi.findUser(12L)).thenReturn(Optional.of(
                new UserSummary(12L, "Locked owner", UserRole.USER, AccountStatus.LOCKED)));

        assertThrows(ShopResubmissionAuthorizationException.class,
                () -> service.resubmit(12L, validFormWithoutLogo()));

        verify(shopRepository, never()).findByOwnerIdForUpdate(any());
        verify(imageStorage, never()).uploadShopLogo(any(), any());
    }

    @Test
    void rejectsBlankDataBeforeSaving() {
        Shop shop = rejectedShop();
        when(shopRepository.findByOwnerIdForUpdate(12L)).thenReturn(Optional.of(shop));
        ShopResubmissionForm form = validFormWithoutLogo();
        form.setShopName("  ");

        assertThrows(ShopResubmissionException.class, () -> service.resubmit(12L, form));

        verify(shopRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsSecondResubmissionAfterStateAlreadyReturnedToPending() {
        Shop shop = rejectedShop();
        when(shopRepository.findByOwnerIdForUpdate(12L)).thenReturn(Optional.of(shop));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.resubmit(12L, validFormWithoutLogo());

        assertThrows(ShopResubmissionException.class,
                () -> service.resubmit(12L, validFormWithoutLogo()));

        verify(shopRepository, org.mockito.Mockito.times(1)).saveAndFlush(any());
    }

    private static Shop rejectedShop() {
        Shop shop = Shop.createPending(12L, "Old shop", "Old description",
                "https://images.example/old-logo.png", "old-logo", "0900000000", "Old address");
        shop.reject(99L, "Thiếu thông tin địa chỉ");
        return shop;
    }

    private static ShopResubmissionForm validFormWithoutLogo() {
        ShopResubmissionForm form = new ShopResubmissionForm();
        form.setShopName("  Updated shop  ");
        form.setDescription("  Updated description  ");
        form.setPhone(" 0900000012 ");
        form.setAddress(" Updated address ");
        return form;
    }
}
