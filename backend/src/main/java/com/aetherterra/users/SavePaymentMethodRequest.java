package com.aetherterra.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SavePaymentMethodRequest(
        @NotBlank
        @Size(max = 50)
        String brand,
        @NotBlank
        @Pattern(regexp = "^\\d{4}$", message = "Card last4 must be exactly 4 digits")
        String last4
) {}
