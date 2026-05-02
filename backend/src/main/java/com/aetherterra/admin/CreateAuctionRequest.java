package com.aetherterra.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateAuctionRequest(
        @NotBlank @Size(min = 3, max = 255) String title,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.01") BigDecimal startingBid,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {}
