package com.aetherterra.bids;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    List<Bid> findByAuctionIdOrderByPlacedAtDesc(UUID auctionId);

    Optional<Bid> findTopByAuctionIdOrderByAmountDescPlacedAtDesc(UUID auctionId);

    long countByAuctionId(UUID auctionId);
}
