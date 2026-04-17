package com.aetherterra.admin;

public record DashboardStatsDto(
        long totalUsers,
        long liveAuctions,
        long totalBids,
        long pendingFulfillments
) {}
