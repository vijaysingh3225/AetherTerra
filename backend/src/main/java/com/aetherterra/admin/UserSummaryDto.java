package com.aetherterra.admin;

import com.aetherterra.users.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserSummaryDto(
        UUID id,
        String email,
        UserRole role,
        String shirtSize,
        boolean emailVerified,
        Instant createdAt
) {}
