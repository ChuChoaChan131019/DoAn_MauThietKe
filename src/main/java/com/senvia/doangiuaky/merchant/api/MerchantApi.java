package com.senvia.doangiuaky.merchant.api;

import java.util.Optional;

/** Public, side-effect-free Merchant contract for other modules. */
@FunctionalInterface
public interface MerchantApi {

    /**
     * Returns a product summary even when the product exists but is not saleable.
     * The returned summary exposes {@link CartProductView#isSaleable()} as the canonical boolean;
     * consumers must not access Merchant internals.
     * An empty result means that the product does not exist or the product ID is invalid.
     */
    Optional<CartProductView> findProductForCart(Long productId);

    /**
     * Returns the order capabilities of an existing shop.
     * An empty result means that the shop does not exist or the shop ID is invalid.
     */
    default Optional<ShopOrderCapabilities> findShopOrderCapabilities(Long shopId) {
        return Optional.empty();
    }
}
