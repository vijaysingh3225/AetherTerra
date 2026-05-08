package com.aetherterra.commerce;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Used when Shopify credentials are absent (local dev / test). Generates a clearly
 * fake checkout URL so the full post-auction flow can be exercised end-to-end locally.
 */
public class MockCommerceOrderProvider implements CommerceOrderProvider {

    private static final Logger log = LoggerFactory.getLogger(MockCommerceOrderProvider.class);

    public MockCommerceOrderProvider() {
        log.warn("CommerceOrderProvider: MOCK mode — no real Shopify orders will be created");
    }

    @Override
    public PostAuctionCheckoutResult createPostAuctionCheckout(PostAuctionCheckoutRequest request) {
        String mockOrderId = "MOCK-" + request.auctionSlug().toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String mockUrl = "http://localhost:8080/mock-checkout/" + mockOrderId;
        log.info("Mock checkout created for auction '{}' (order {}) winner {} — ref: {} url: {}",
                request.auctionSlug(), request.auctionOrderId(), request.winnerEmail(), mockOrderId, mockUrl);
        return new PostAuctionCheckoutResult(providerName(), mockOrderId, mockUrl);
    }

    @Override
    public String providerName() {
        return "MOCK";
    }
}
