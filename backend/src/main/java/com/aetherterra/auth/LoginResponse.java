package com.aetherterra.auth;

public record LoginResponse(String token, String email, String role) {}
