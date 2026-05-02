package com.aetherterra.commerce;

/**
 * Abstracts post-auction winner checkout creation. Shopify-backed in production;
 * mock (generates a local fake URL) when Shopify credentials are absent.
 */
public interface CommerceOrderProvider {

    /**
     * Creates a checkout / draft order for the auction winner.
     * Returns the checkout URL and a provider-specific order reference.
     * Must be idempotent for the same auctionId.
     */
    PostAuctionCheckoutResult createPostAuctionCheckout(PostAuctionCheckoutRequest request);

    /** Provider name surfaced in admin UI and stored on the order record. */
    String providerName();
}
