package com.aetherterra.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateAuctionRequest(
        @NotBlank String title,
        String description,
        @NotNull @DecimalMin("0.01") BigDecimal startingBid,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {}
