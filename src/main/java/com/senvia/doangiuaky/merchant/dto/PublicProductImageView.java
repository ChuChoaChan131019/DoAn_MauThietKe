package com.senvia.doangiuaky.merchant.dto;

/** Public product-image data; Cloudinary internal identifiers are intentionally omitted. */
public record PublicProductImageView(
        String imageUrl,
        int displayOrder) {
}
