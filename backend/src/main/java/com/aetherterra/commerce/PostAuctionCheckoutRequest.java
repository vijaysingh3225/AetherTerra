package com.aetherterra.commerce;

import java.math.BigDecimal;
import java.util.UUID;

public record PostAuctionCheckoutRequest(
        UUID auctionOrderId,
        UUID auctionId,
        String auctionSlug,
        String auctionTitle,
        String winnerEmail,
        BigDecimal winningBid,
        String shirtSize,
        UUID winningBidId,
        UUID userId
) {}
