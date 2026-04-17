package com.aetherterra.auctions;

import com.aetherterra.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuctionSummaryDto>>> listAuctions() {
        // TODO: replace with real repository query once Auction entity is wired up
        var mockAuctions = List.of(
            new AuctionSummaryDto(
                "1",
                "terra-one",
                "Terra One — Hand-printed Oversized Tee",
                new BigDecimal("75.00"),
                Instant.now().plus(2, ChronoUnit.DAYS),
                AuctionStatus.LIVE
            ),
            new AuctionSummaryDto(
                "2",
                "void-series-01",
                "Void Series 01 — Garment Dyed Drop",
                new BigDecimal("120.00"),
                Instant.now().plus(5, ChronoUnit.DAYS),
                AuctionStatus.LIVE
            )
        );
        return ResponseEntity.ok(ApiResponse.ok(mockAuctions));
    }
}
