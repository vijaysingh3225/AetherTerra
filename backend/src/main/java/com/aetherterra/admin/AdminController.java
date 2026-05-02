package com.aetherterra.admin;

import com.aetherterra.auctions.Auction;
import com.aetherterra.auctions.AuctionRepository;
import com.aetherterra.auctions.AuctionStatus;
import com.aetherterra.bids.BidRepository;
import com.aetherterra.common.ApiResponse;
import com.aetherterra.orders.AuctionOrderRepository;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionOrderRepository auctionOrderRepository;

    public AdminController(UserRepository userRepository,
                           AuctionRepository auctionRepository,
                           BidRepository bidRepository,
                           AuctionOrderRepository auctionOrderRepository) {
        this.userRepository = userRepository;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.auctionOrderRepository = auctionOrderRepository;
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> dashboard() {
        long pendingFulfillments = auctionOrderRepository
                .countByStatus(com.aetherterra.orders.AuctionOrderStatus.PENDING_PAYMENT);
        var stats = new DashboardStatsDto(
                userRepository.count(),
                auctionRepository.countByStatus(AuctionStatus.LIVE),
                bidRepository.count(),
                pendingFulfillments
        );
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @GetMapping("/auctions/{id}/order")
    public ResponseEntity<?> getAuctionOrder(@PathVariable UUID id) {
        if (auctionRepository.findById(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.message("Auction not found"));
        }
        return auctionOrderRepository.findByAuctionId(id)
                .<ResponseEntity<?>>map(order -> ResponseEntity.ok(ApiResponse.ok(toOrderDto(order))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.message("No order for this auction")));
    }

    // ── Users ─────────────────────────────────────────────────────────────────

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

    // ── Auctions ──────────────────────────────────────────────────────────────

    @GetMapping("/auctions")
    public ResponseEntity<ApiResponse<Page<AuctionAdminDto>>> listAuctions(
            @RequestParam(required = false) AuctionStatus status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Auction> auctions;
        if (status != null && !search.isBlank()) {
            auctions = auctionRepository.findByStatusAndTitleContainingIgnoreCase(status, search, pageable);
        } else if (status != null) {
            auctions = auctionRepository.findByStatus(status, pageable);
        } else if (!search.isBlank()) {
            auctions = auctionRepository.findByTitleContainingIgnoreCase(search, pageable);
        } else {
            auctions = auctionRepository.findAll(pageable);
        }

        return ResponseEntity.ok(ApiResponse.ok(
                auctions.map(a -> toAdminDto(a, bidRepository.countByAuctionId(a.getId())))));
    }

    @PostMapping("/auctions")
    public ResponseEntity<ApiResponse<AuctionAdminDto>> createAuction(
            @Valid @RequestBody CreateAuctionRequest req,
            Authentication auth) {

        if (!req.endsAt().isAfter(req.startsAt())) {
            return ResponseEntity.badRequest().body(ApiResponse.message("End time must be after start time"));
        }

        var creator = userRepository.findByEmail(auth.getName()).orElseThrow();

        var auction = new Auction();
        auction.setTitle(req.title());
        auction.setSlug(generateSlug(req.title()));
        auction.setDescription(req.description());
        auction.setStartingBid(req.startingBid());
        auction.setStartsAt(req.startsAt());
        auction.setEndsAt(req.endsAt());
        auction.setCreatedById(creator.getId());

        var saved = auctionRepository.save(auction);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(toAdminDto(saved, 0L)));
    }

    @PatchMapping("/auctions/{id}")
    public ResponseEntity<ApiResponse<AuctionAdminDto>> updateAuction(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAuctionRequest req) {

        var opt = auctionRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.message("Auction not found"));
        }

        var auction = opt.get();

        if (auction.getStatus() == AuctionStatus.ENDED || auction.getStatus() == AuctionStatus.CANCELLED) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.message("Cannot modify an ended or cancelled auction"));
        }

        if (auction.getStatus() == AuctionStatus.LIVE) {
            if (req.status() != AuctionStatus.CANCELLED) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(ApiResponse.message("A live auction can only be cancelled"));
            }
            auction.setStatus(AuctionStatus.CANCELLED);
        } else {
            // SCHEDULED: allow full edits
            if (req.title() != null) auction.setTitle(req.title());
            if (req.description() != null) auction.setDescription(req.description());
            if (req.startingBid() != null) auction.setStartingBid(req.startingBid());
            if (req.startsAt() != null) auction.setStartsAt(req.startsAt());
            if (req.endsAt() != null) auction.setEndsAt(req.endsAt());
            if (req.status() != null) {
                if (req.status() == AuctionStatus.ENDED) {
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(ApiResponse.message("Cannot manually set status to ENDED"));
                }
                auction.setStatus(req.status());
            }
            if (!auction.getEndsAt().isAfter(auction.getStartsAt())) {
                return ResponseEntity.badRequest().body(ApiResponse.message("End time must be after start time"));
            }
        }

        var saved = auctionRepository.save(auction);
        return ResponseEntity.ok(ApiResponse.ok(toAdminDto(saved, bidRepository.countByAuctionId(saved.getId()))));
    }

    @DeleteMapping("/auctions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAuction(@PathVariable UUID id) {
        var opt = auctionRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.message("Auction not found"));
        }

        var auction = opt.get();
        if (auction.getStatus() != AuctionStatus.SCHEDULED) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.message("Only scheduled auctions can be deleted"));
        }
        if (bidRepository.countByAuctionId(id) > 0) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.message("Cannot delete an auction that has bids. Cancel it instead."));
        }

        auctionRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.message("Auction deleted"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuctionAdminDto toAdminDto(Auction a, long bidCount) {
        return new AuctionAdminDto(
                a.getId(), a.getSlug(), a.getTitle(), a.getDescription(), a.getStatus(),
                a.getStartingBid(), a.getCurrentBid(), a.getStartsAt(), a.getEndsAt(),
                a.getCreatedById(), a.getCreatedAt(), a.getUpdatedAt(), bidCount);
    }

    private com.aetherterra.admin.AuctionOrderDto toOrderDto(com.aetherterra.orders.AuctionOrder o) {
        return new com.aetherterra.admin.AuctionOrderDto(
                o.getId(), o.getAuctionId(), o.getUserId(),
                o.getAmount(), o.getCurrency(), o.getShirtSize(),
                o.getProvider(), o.getProviderOrderId(), o.getCheckoutUrl(),
                o.getStatus().name(), o.getCreatedAt(), o.getUpdatedAt()
        );
    }

    private String generateSlug(String title) {
        String base = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
        if (base.isBlank()) base = "auction";
        String candidate = base;
        int suffix = 1;
        while (auctionRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
