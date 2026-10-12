package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.dto.ShopUpdateForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopProfileServiceTest {

    private static final Long OWNER_ID = 20L;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private IdentityApi identityApi;

    @Mock
    private MerchantImageStorage imageStorage;

    private ShopProfileService service;

    @BeforeEach
    void setUp() {
        service = new ShopProfileService(shopRepository, identityApi, imageStorage);
        when(identityApi.findUser(OWNER_ID))
                .thenReturn(Optional.of(new UserSummary(OWNER_ID, "Owner", UserRole.USER, AccountStatus.ACTIVE)));
    }

    @Test
    void approvedOwnerCanReplaceLogoAndOldAssetIsCleaned() {
        Shop shop = approvedShop("shops/20/old");
        when(shopRepository.findByOwnerIdForUpdate(OWNER_ID)).thenReturn(Optional.of(shop));
        when(imageStorage.uploadShopLogo(eq(OWNER_ID), any()))
                .thenReturn(new MerchantImageAsset("https://cloudinary.test/new.png", "shops/20/new"));
        when(shopRepository.saveAndFlush(shop)).thenReturn(shop);

        Shop saved = service.update(OWNER_ID, form(newLogo()));

        assertEquals(ShopStatus.APPROVED, saved.getStatus());
        assertEquals("https://cloudinary.test/new.png", saved.getLogoUrl());
        assertEquals("shops/20/new", saved.getLogoPublicId());
        verify(imageStorage).delete("shops/20/old");
    }

    @Test
    void approvedOwnerCanRemoveLogoWithoutSendingPublicId() {
        Shop shop = approvedShop("shops/20/old");
        when(shopRepository.findByOwnerIdForUpdate(OWNER_ID)).thenReturn(Optional.of(shop));
        when(shopRepository.saveAndFlush(shop)).thenReturn(shop);
        ShopUpdateForm form = form(null);
        form.setRemoveLogo(true);

        service.update(OWNER_ID, form);

        assertEquals(null, shop.getLogoUrl());
        assertEquals(null, shop.getLogoPublicId());
        verify(imageStorage).delete("shops/20/old");
        verify(imageStorage, never()).uploadShopLogo(any(), any());
    }

    @Test
    void databaseFailureCleansNewLogoAndKeepsOldReference() {
        Shop shop = approvedShop("shops/20/old");
        when(shopRepository.findByOwnerIdForUpdate(OWNER_ID)).thenReturn(Optional.of(shop));
        when(imageStorage.uploadShopLogo(eq(OWNER_ID), any()))
                .thenReturn(new MerchantImageAsset("https://cloudinary.test/new.png", "shops/20/new"));
        when(shopRepository.saveAndFlush(shop)).thenThrow(new IllegalStateException("database failure"));

        assertThrows(IllegalStateException.class, () -> service.update(OWNER_ID, form(newLogo())));

        verify(imageStorage).delete("shops/20/new");
        verify(imageStorage, never()).delete("shops/20/old");
    }

    @Test
    void nonApprovedShopCannotUpdateOrUploadLogo() {
        Shop shop = Shop.createPending(OWNER_ID, "Shop", "Description", null, null, "0900000000", "Address");
        when(shopRepository.findByOwnerIdForUpdate(OWNER_ID)).thenReturn(Optional.of(shop));

        assertThrows(ShopProfileAuthorizationException.class,
                () -> service.update(OWNER_ID, form(newLogo())));

        verify(imageStorage, never()).uploadShopLogo(any(), any());
    }

    private Shop approvedShop(String publicId) {
        Shop shop = Shop.createPending(OWNER_ID, "Shop", "Description",
                "https://cloudinary.test/old.png", publicId, "0900000000", "Address");
        shop.approve(99L, Instant.now());
        return shop;
    }

    private static ShopUpdateForm form(MockMultipartFile logo) {
        ShopUpdateForm form = new ShopUpdateForm();
        form.setShopName("Updated shop");
        form.setDescription("Updated description");
        form.setPhone("0900000000");
        form.setAddress("Updated address");
        form.setLogo(logo);
        return form;
    }

    private static MockMultipartFile newLogo() {
        return new MockMultipartFile(
                "logo",
                "logo.png",
                "image/png",
                java.util.Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
    }
}
