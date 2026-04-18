package com.aetherterra.admin;

import com.aetherterra.common.ApiResponse;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserSummaryDto>>> listUsers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) UserRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserSummaryDto> result = (role != null
                ? userRepository.findByEmailContainingIgnoreCaseAndRole(search, role, pageable)
                : userRepository.findByEmailContainingIgnoreCase(search, pageable))
                .map(u -> new UserSummaryDto(
                        u.getId(),
                        u.getEmail(),
                        u.getRole(),
                        u.getShirtSize(),
                        u.getEmailVerifiedAt() != null,
                        u.getCreatedAt()
                ));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID id) {
        var found = userRepository.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.message("User not found"));
        }
        if (found.get().getRole() == UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.message("Cannot delete admin users"));
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.message("User deleted"));
    }
}
