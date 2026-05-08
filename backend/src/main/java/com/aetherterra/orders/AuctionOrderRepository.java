package com.aetherterra.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionOrderRepository extends JpaRepository<AuctionOrder, UUID> {
    Optional<AuctionOrder> findByAuctionId(UUID auctionId);
    long countByStatus(AuctionOrderStatus status);
    List<AuctionOrder> findAllByStatusAndPaymentDueAtBefore(AuctionOrderStatus status, Instant cutoff);
}
