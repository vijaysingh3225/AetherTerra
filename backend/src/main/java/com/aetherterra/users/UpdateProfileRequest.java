package com.aetherterra.users;

import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        @Pattern(
                regexp = "^(XS|S|M|L|XL|XXL)$",
                message = "Shirt size must be one of XS, S, M, L, XL, or XXL"
        )
        String shirtSize
) {}
