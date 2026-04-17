package com.aetherterra.auctions;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionSummaryDto(
    String id,
    String slug,
    String title,
    BigDecimal currentBid,
    Instant endsAt,
    AuctionStatus status
) {}
