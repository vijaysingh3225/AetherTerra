package com.aetherterra.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionOrderDto(
        UUID id,
        UUID auctionId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String shirtSize,
        String provider,
        boolean mockProvider,
        String providerOrderId,
        String checkoutUrl,
        String status,
        Instant paymentDueAt,
        Instant paidAt,
        Instant expiredAt,
        Instant createdAt,
        Instant updatedAt
) {}
