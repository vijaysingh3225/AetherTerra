package com.aetherterra.commerce;

public record PostAuctionCheckoutResult(
        String provider,
        String providerOrderId,
        String checkoutUrl
) {}
