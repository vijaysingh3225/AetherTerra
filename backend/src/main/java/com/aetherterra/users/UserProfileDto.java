package com.aetherterra.users;

import java.time.Instant;

public record UserProfileDto(
        String email,
        String role,
        String shirtSize,
        boolean emailVerified,
        String paymentMethodBrand,
        String paymentMethodLast4,
        Instant paymentMethodAddedAt
) {}
