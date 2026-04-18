package com.aetherterra.auctions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    List<Auction> findByStatusInOrderByEndsAtAsc(Collection<AuctionStatus> statuses);

    Optional<Auction> findBySlug(String slug);
}
