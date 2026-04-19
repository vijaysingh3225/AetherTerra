package com.aetherterra.bids;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlaceBidRequest(
        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount
) {}
