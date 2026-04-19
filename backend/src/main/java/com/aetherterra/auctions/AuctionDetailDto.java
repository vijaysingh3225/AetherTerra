package com.aetherterra.auctions;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionDetailDto(
        String id,
        String slug,
        String title,
        String description,
        BigDecimal startingBid,
        BigDecimal currentBid,
        Instant startsAt,
        Instant endsAt,
        AuctionStatus status,
        long bidCount
) {}
