package com.aetherterra.admin;

import com.aetherterra.auctions.AuctionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionAdminDto(
        UUID id,
        String slug,
        String title,
        String description,
        AuctionStatus status,
        BigDecimal startingBid,
        BigDecimal currentBid,
        Instant startsAt,
        Instant endsAt,
        UUID createdById,
        Instant createdAt,
        Instant updatedAt,
        long bidCount
) {}
