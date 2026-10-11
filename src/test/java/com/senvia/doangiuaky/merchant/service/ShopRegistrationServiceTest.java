package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.dto.ShopRegistrationForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class ShopRegistrationServiceTest {

    private final ShopRepository shopRepository = mock(ShopRepository.class);
    private final IdentityApi identityApi = mock(IdentityApi.class);
    private final MerchantImageStorage imageStorage = mock(MerchantImageStorage.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ShopRegistrationService service = new ShopRegistrationService(
            shopRepository, identityApi, imageStorage, eventPublisher);

    @BeforeEach
    void setUpEligibleUser() {
        when(identityApi.findUser(12L)).thenReturn(Optional.of(
                new UserSummary(12L, "Seller", UserRole.USER, AccountStatus.ACTIVE)));
        when(identityApi.isUserActive(12L)).thenReturn(true);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void submitsPendingShopWithUploadedLogo() {
        ShopRegistrationForm form = validForm();
        when(shopRepository.existsByOwnerId(12L)).thenReturn(false);
        when(imageStorage.uploadShopLogo(eq(12L), any())).thenReturn(
                new MerchantImageAsset("https://images.example/logo.png", "senvia/logo-1"));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shop shop = service.submit(12L, form);

        assertEquals(ShopStatus.PENDING, shop.getStatus());
        assertEquals("My shop", shop.getShopName());
        assertEquals("https://images.example/logo.png", shop.getLogoUrl());
        assertEquals("senvia/logo-1", shop.getLogoPublicId());
        verify(shopRepository).saveAndFlush(any(Shop.class));
    }

    @Test
    void rejectsDuplicateRequestBeforeUploadingLogo() {
        when(shopRepository.existsByOwnerId(12L)).thenReturn(true);

        assertThrows(ShopRegistrationException.class, () -> service.submit(12L, validForm()));

        verify(imageStorage, never()).uploadShopLogo(any(), any());
        verify(shopRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAccountThatIsNotAUser() {
        when(identityApi.findUser(12L)).thenReturn(Optional.of(
                new UserSummary(12L, "Admin", UserRole.ADMIN, AccountStatus.ACTIVE)));

        assertThrows(ShopRegistrationException.class, () -> service.submit(12L, validForm()));

        verify(shopRepository, never()).existsByOwnerId(any());
        verify(imageStorage, never()).uploadShopLogo(any(), any());
    }

    @Test
    void rejectsMissingAccountBeforeCheckingShopOrUploadingLogo() {
        when(identityApi.findUser(12L)).thenReturn(Optional.empty());

        assertThrows(ShopRegistrationException.class, () -> service.submit(12L, validForm()));

        verify(shopRepository, never()).existsByOwnerId(any());
        verify(imageStorage, never()).uploadShopLogo(any(), any());
    }

    @Test
    void rejectsInactiveAccountBeforeCheckingShopOrUploadingLogo() {
        when(identityApi.isUserActive(12L)).thenReturn(false);

        assertThrows(ShopRegistrationException.class, () -> service.submit(12L, validForm()));

        verify(shopRepository, never()).existsByOwnerId(any());
        verify(imageStorage, never()).uploadShopLogo(any(), any());
    }

    @Test
    void rejectsInvalidOwnerBeforeCallingIdentityApi() {
        assertThrows(ShopRegistrationException.class, () -> service.submit(null, validForm()));

        verify(identityApi, never()).findUser(any());
        verify(shopRepository, never()).existsByOwnerId(any());
        verify(imageStorage, never()).uploadShopLogo(any(), any());
    }

    @Test
    void deletesUploadedLogoWhenDatabaseSaveFails() {
        ShopRegistrationForm form = validForm();
        when(shopRepository.existsByOwnerId(12L)).thenReturn(false);
        when(imageStorage.uploadShopLogo(eq(12L), any())).thenReturn(
                new MerchantImageAsset("https://images.example/logo.png", "senvia/logo-2"));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.submit(12L, form));

        verify(imageStorage).delete("senvia/logo-2");
    }

    @Test
    void convertsConcurrentDuplicateIntoRegistrationErrorAndDeletesLogo() {
        when(shopRepository.existsByOwnerId(12L)).thenReturn(false);
        when(imageStorage.uploadShopLogo(eq(12L), any())).thenReturn(
                new MerchantImageAsset("https://images.example/logo.png", "senvia/logo-3"));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenThrow(new DataIntegrityViolationException("duplicate owner"));

        assertThrows(ShopRegistrationException.class, () -> service.submit(12L, validForm()));

        verify(imageStorage).delete("senvia/logo-3");
    }

    @Test
    void deletesUploadedLogoWhenTransactionRollsBackAfterFlush() {
        when(shopRepository.existsByOwnerId(12L)).thenReturn(false);
        when(imageStorage.uploadShopLogo(eq(12L), any())).thenReturn(
                new MerchantImageAsset("https://images.example/logo.png", "senvia/logo-rollback"));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.submit(12L, validForm());
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(imageStorage).delete("senvia/logo-rollback");
    }

    @Test
    void keepsUploadedLogoWhenTransactionCommits() {
        when(shopRepository.existsByOwnerId(12L)).thenReturn(false);
        when(imageStorage.uploadShopLogo(eq(12L), any())).thenReturn(
                new MerchantImageAsset("https://images.example/logo.png", "senvia/logo-commit"));
        when(shopRepository.saveAndFlush(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.submit(12L, validForm());
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(imageStorage, never()).delete("senvia/logo-commit");
    }

    @Test
    void reportsMissingOwnedShopWithSpecificException() {
        when(shopRepository.findByOwnerId(12L)).thenReturn(Optional.empty());

        assertThrows(ShopNotFoundException.class, () -> service.getOwnedShop(12L));
    }

    private static ShopRegistrationForm validForm() {
        ShopRegistrationForm form = new ShopRegistrationForm();
        form.setShopName("  My shop  ");
        form.setDescription("  Handcrafted items  ");
        form.setPhone("0900000000");
        form.setAddress("  Ho Chi Minh City  ");
        form.setLogo(new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1}));
        return form;
    }
}
