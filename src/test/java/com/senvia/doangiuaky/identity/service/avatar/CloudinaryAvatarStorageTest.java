package com.senvia.doangiuaky.identity.service.avatar;

import com.cloudinary.Cloudinary;
import com.senvia.doangiuaky.common.config.CloudinaryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudinaryAvatarStorageTest {

    @Test
    void rejectsUnsupportedMimeTypesBeforeCallingCloudinary() {
        CloudinaryAvatarStorage storage = new CloudinaryAvatarStorage(
                cloudinary(),
                new CloudinaryProperties("cloud", "key", "secret"));
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "avatar.txt", "text/plain", "not an image".getBytes());

        assertThrows(AvatarStorageException.class, () -> storage.upload(4L, file));
    }

    @Test
    void reportsMissingCloudinaryConfiguration() {
        CloudinaryAvatarStorage storage = new CloudinaryAvatarStorage(
                cloudinary(),
                new CloudinaryProperties("", "", ""));
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", new byte[] {1});

        assertThrows(AvatarStorageException.class, () -> storage.upload(4L, file));
    }

    @Test
    void rejectsImageMimeTypeWhenContentIsNotAnImage() {
        CloudinaryAvatarStorage storage = new CloudinaryAvatarStorage(
                cloudinary(),
                new CloudinaryProperties("cloud", "key", "secret"));
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "avatar.png", "image/png", "not an image".getBytes());

        assertThrows(AvatarStorageException.class, () -> storage.upload(4L, file));
    }

    @Test
    void rejectsAvatarLargerThanTwoMegabytes() {
        CloudinaryAvatarStorage storage = new CloudinaryAvatarStorage(
                cloudinary(),
                new CloudinaryProperties("cloud", "key", "secret"));
        byte[] content = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(content, (byte) 1);
        MockMultipartFile file = new MockMultipartFile(
                "avatar", "avatar.jpg", "image/jpeg", content);

        assertThrows(AvatarStorageException.class, () -> storage.upload(4L, file));
    }

    private static Cloudinary cloudinary() {
        return new Cloudinary(Map.of(
                "cloud_name", "cloud",
                "api_key", "key",
                "api_secret", "secret"));
    }
}
