package com.aetherterra.admin;

import com.aetherterra.common.ApiResponse;
import com.aetherterra.users.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> dashboard() {
        var stats = new DashboardStatsDto(
                userRepository.count(),
                0L, // TODO: query AuctionRepository by status=LIVE once entity is built
                0L, // TODO: query BidRepository.count() once entity is built
                0L  // TODO: post-payment fulfillment flow (Shopify v2)
        );
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
