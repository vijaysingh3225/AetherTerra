package com.aetherterra.auctions;

import com.aetherterra.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final AuctionRepository auctionRepository;

    public AuctionController(AuctionRepository auctionRepository) {
        this.auctionRepository = auctionRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuctionSummaryDto>>> listAuctions() {
        var auctions = auctionRepository
            .findByStatusInOrderByEndsAtAsc(List.of(AuctionStatus.LIVE, AuctionStatus.SCHEDULED, AuctionStatus.ENDED))
            .stream()
            .map(this::toSummary)
            .toList();
        return ResponseEntity.ok(ApiResponse.ok(auctions));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<AuctionSummaryDto>> getAuction(@PathVariable String slug) {
        return auctionRepository.findBySlug(slug)
            .map(a -> ResponseEntity.ok(ApiResponse.ok(toSummary(a))))
            .orElse(ResponseEntity.status(404).body(ApiResponse.message("Auction not found")));
    }

    private AuctionSummaryDto toSummary(Auction a) {
        return new AuctionSummaryDto(
            a.getId().toString(),
            a.getSlug(),
            a.getTitle(),
            a.getDescription(),
            a.getStartingBid(),
            a.getCurrentBid(),
            a.getStartsAt(),
            a.getEndsAt(),
            a.getStatus()
        );
    }
}
