package com.aetherterra.admin;

import com.aetherterra.auctions.AuctionStatus;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateAuctionRequest(
        String title,
        String description,
        @DecimalMin("0.01") BigDecimal startingBid,
        Instant startsAt,
        Instant endsAt,
        AuctionStatus status
) {}
