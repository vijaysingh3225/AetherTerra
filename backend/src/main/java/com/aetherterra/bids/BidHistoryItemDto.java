package com.aetherterra.bids;

import java.math.BigDecimal;
import java.time.Instant;

public record BidHistoryItemDto(
        String id,
        BigDecimal amount,
        Instant placedAt,
        String bidder
) {}
