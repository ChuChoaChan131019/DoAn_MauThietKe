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
     * Returns the authoritative product summary for a cart mutation while holding a database
     * write lock on the product row until the caller's transaction completes.
     *
     * <p>The caller must already have an active transaction. Implementations must not change
     * stock and must return an existing but unavailable product so Shopping can report the
     * precise reason. This separate command-side query prevents Shopping from persisting a
     * quantity based on a stock snapshot read before it acquired the cart lock.</p>
     */
    default Optional<CartProductView> findProductForCartMutation(Long productId) {
        return findProductForCart(productId);
    }

    /**
     * Returns the order capabilities of an existing shop.
     * An empty result means that the shop does not exist or the shop ID is invalid.
     */
    default Optional<ShopOrderCapabilities> findShopOrderCapabilities(Long shopId) {
        return Optional.empty();
    }

    /** Returns a minimal shop summary even when the shop is not publicly listed. */
    default Optional<MerchantShopSummary> findShopSummary(Long shopId) {
        return Optional.empty();
    }
}
