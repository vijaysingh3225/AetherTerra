package com.aetherterra.users;

import java.time.Instant;

public record UserProfileDto(
        String email,
        String role,
        String shirtSize,
        boolean emailVerified,
        boolean paymentMethodReady,
        Instant paymentMethodAddedAt
) {}
