package com.aetherterra.bids;

import com.aetherterra.auctions.Auction;
import com.aetherterra.auctions.AuctionRepository;
import com.aetherterra.auctions.AuctionStatus;
import com.aetherterra.common.ApiResponse;
import com.aetherterra.users.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions/{slug}/bids")
public class BidController {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;

    public BidController(
            AuctionRepository auctionRepository,
            BidRepository bidRepository,
            UserRepository userRepository) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BidHistoryItemDto>>> listBids(@PathVariable String slug) {
        Auction auction = auctionRepository.findBySlug(slug).orElse(null);
        if (auction == null) {
            return ResponseEntity.status(404).body(ApiResponse.message("Auction not found"));
        }

        var bidderLabels = userRepository.findAllById(
                bidRepository.findByAuctionIdOrderByPlacedAtDesc(auction.getId()).stream()
                        .map(Bid::getBidderId)
                        .distinct()
                        .toList()
        ).stream().collect(java.util.stream.Collectors.toMap(
                user -> user.getId(),
                user -> maskEmail(user.getEmail())
        ));

        var bids = bidRepository.findByAuctionIdOrderByPlacedAtDesc(auction.getId()).stream()
                .map(bid -> new BidHistoryItemDto(
                        bid.getId().toString(),
                        bid.getAmount(),
                        bid.getPlacedAt(),
                        bidderLabels.getOrDefault(bid.getBidderId(), "Anonymous bidder")
                ))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(bids));
    }

    @Transactional
    @PostMapping
    public ResponseEntity<?> placeBid(
            @PathVariable String slug,
            @Valid @RequestBody PlaceBidRequest request,
            Authentication authentication) {
        Auction auction = auctionRepository.findBySlug(slug).orElse(null);
        if (auction == null) {
            return ResponseEntity.status(404).body(ApiResponse.message("Auction not found"));
        }

        if (auction.getStatus() != AuctionStatus.LIVE) {
            return ResponseEntity.badRequest().body(ApiResponse.message("This auction is not accepting bids"));
        }

        Instant now = Instant.now();
        if (auction.getStartsAt().isAfter(now) || auction.getEndsAt().isBefore(now)) {
            return ResponseEntity.badRequest().body(ApiResponse.message("This auction is outside its live bidding window"));
        }

        String email = authentication.getName();
        var user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.message("User not found"));
        }

        if (!user.isEmailVerified()) {
            return ResponseEntity.status(403).body(ApiResponse.message("Verify your email before placing a bid"));
        }

        if (!user.hasShirtSize()) {
            return ResponseEntity.status(403).body(ApiResponse.message("Select your shirt size before placing a bid"));
        }

        if (!user.isPaymentMethodReady()) {
            return ResponseEntity.status(403).body(ApiResponse.message("Save a payment method before placing a bid"));
        }

        if (auction.getCreatedById().equals(user.getId())) {
            return ResponseEntity.badRequest().body(ApiResponse.message("Auction creators cannot bid on their own auctions"));
        }

        BigDecimal floor = auction.getCurrentBid() != null ? auction.getCurrentBid() : auction.getStartingBid();
        if (request.amount().compareTo(floor) <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.message("Bid must be greater than the current bid"));
        }

        var bid = new Bid();
        bid.setAuctionId(auction.getId());
        bid.setBidderId(user.getId());
        bid.setAmount(request.amount());
        bid.setPlacedAt(now);
        bidRepository.save(bid);

        auction.setCurrentBid(request.amount());
        auctionRepository.save(auction);

        return ResponseEntity.status(201).body(ApiResponse.ok(
                new BidHistoryItemDto(
                        bid.getId().toString(),
                        bid.getAmount(),
                        bid.getPlacedAt(),
                        maskEmail(user.getEmail())
                )
        ));
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        String start = email.substring(0, Math.min(2, atIndex));
        String domain = email.substring(atIndex);
        return start + "***" + domain;
    }
}
