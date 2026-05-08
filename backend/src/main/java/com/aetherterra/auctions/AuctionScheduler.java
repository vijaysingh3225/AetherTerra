package com.aetherterra.auctions;

import com.aetherterra.bids.Bid;
import com.aetherterra.bids.BidRepository;
import com.aetherterra.commerce.CommerceOrderProvider;
import com.aetherterra.commerce.PostAuctionCheckoutRequest;
import com.aetherterra.commerce.PostAuctionCheckoutResult;
import com.aetherterra.notification.NotificationService;
import com.aetherterra.orders.AuctionOrder;
import com.aetherterra.orders.AuctionOrderRepository;
import com.aetherterra.orders.AuctionOrderStatus;
import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
public class AuctionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final AuctionOrderRepository auctionOrderRepository;
    private final CommerceOrderProvider commerceOrderProvider;
    private final NotificationService notificationService;
    private final long paymentDueHours;

    public AuctionScheduler(AuctionRepository auctionRepository,
                             BidRepository bidRepository,
                             UserRepository userRepository,
                             AuctionOrderRepository auctionOrderRepository,
                             CommerceOrderProvider commerceOrderProvider,
                             NotificationService notificationService,
                             @Value("${aetherterra.payment.due-hours:24}") long paymentDueHours) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.userRepository = userRepository;
        this.auctionOrderRepository = auctionOrderRepository;
        this.commerceOrderProvider = commerceOrderProvider;
        this.notificationService = notificationService;
        this.paymentDueHours = paymentDueHours;
    }

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    @Transactional
    public void transitionAuctions() {
        Instant now = Instant.now();
        activateScheduled(now);
        endLive(now);
    }

    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    @Transactional
    public void expireOverduePayments() {
        List<AuctionOrder> overdue = auctionOrderRepository
                .findAllByStatusAndPaymentDueAtBefore(AuctionOrderStatus.PENDING_PAYMENT, Instant.now());

        for (AuctionOrder order : overdue) {
            order.setStatus(AuctionOrderStatus.EXPIRED);
            order.setExpiredAt(Instant.now());
            order.setFailureReason("Payment deadline exceeded");
            auctionOrderRepository.save(order);
            log.info("AuctionOrder {} (auction {}) marked EXPIRED — payment deadline was {}",
                    order.getId(), order.getAuctionId(), order.getPaymentDueAt());
        }
    }

    private void activateScheduled(Instant now) {
        List<Auction> toActivate = auctionRepository
                .findByStatusInOrderByEndsAtAsc(List.of(AuctionStatus.SCHEDULED))
                .stream()
                .filter(a -> !a.getStartsAt().isAfter(now))
                .toList();

        for (Auction a : toActivate) {
            a.setStatus(AuctionStatus.LIVE);
            auctionRepository.save(a);
            log.info("Auction '{}' is now LIVE", a.getSlug());
        }
    }

    private void endLive(Instant now) {
        List<Auction> toEnd = auctionRepository
                .findByStatusInOrderByEndsAtAsc(List.of(AuctionStatus.LIVE))
                .stream()
                .filter(a -> !a.getEndsAt().isAfter(now))
                .toList();

        for (Auction a : toEnd) {
            a.setStatus(AuctionStatus.ENDED);
            Optional<Bid> winningBid = bidRepository.findTopByAuctionIdOrderByAmountDescPlacedAtDesc(a.getId());
            winningBid.ifPresent(bid -> a.setWinnerId(bid.getBidderId()));
            auctionRepository.save(a);
            log.info("Auction '{}' ENDED — winner: {}", a.getSlug(), a.getWinnerId());

            if (a.getWinnerId() != null) {
                processAuctionClose(a, winningBid.orElse(null));
            }
        }
    }

    /**
     * Creates a winner checkout order via the CommerceOrderProvider and notifies the winner.
     * Errors are logged but never propagate — the auction is already ENDED.
     * Idempotent: skips if an order already exists for this auction.
     *
     * Two-step save: the order is persisted first so we have a stable UUID to pass to the
     * commerce provider as metadata. Shopify embeds it in the Draft Order's custom_attributes
     * so the orders/paid webhook can find the order directly by ID rather than via auction_id.
     */
    private void processAuctionClose(Auction auction, Bid winningBid) {
        if (auctionOrderRepository.findByAuctionId(auction.getId()).isPresent()) {
            log.debug("Order already exists for auction '{}'; skipping", auction.getSlug());
            return;
        }

        User winner = userRepository.findById(auction.getWinnerId()).orElse(null);
        if (winner == null) {
            log.error("Winner user {} not found for auction '{}'", auction.getWinnerId(), auction.getSlug());
            return;
        }

        // Step 1 — persist the order now so we have a stable UUID for the provider call.
        // Provider name is known before calling; providerOrderId and checkoutUrl come back after.
        AuctionOrder order = new AuctionOrder();
        order.setAuctionId(auction.getId());
        order.setUserId(winner.getId());
        if (winningBid != null) order.setWinningBidId(winningBid.getId());
        order.setAmount(auction.getCurrentBid());
        order.setShirtSize(winner.getShirtSize());
        order.setProvider(commerceOrderProvider.providerName());
        order.setStatus(AuctionOrderStatus.PENDING_PAYMENT);
        AuctionOrder saved = auctionOrderRepository.save(order);

        // Step 2 — call the provider with the stable order UUID embedded in the request.
        try {
            var request = new PostAuctionCheckoutRequest(
                    saved.getId(),
                    auction.getId(),
                    auction.getSlug(),
                    auction.getTitle(),
                    winner.getEmail(),
                    auction.getCurrentBid(),
                    winner.getShirtSize(),
                    winningBid != null ? winningBid.getId() : null,
                    winner.getId()
            );
            PostAuctionCheckoutResult result = commerceOrderProvider.createPostAuctionCheckout(request);

            // Step 3 — update with provider-specific results.
            saved.setProviderOrderId(result.providerOrderId());
            saved.setCheckoutUrl(result.checkoutUrl());
            saved.setPaymentDueAt(Instant.now().plus(paymentDueHours, ChronoUnit.HOURS));
            auctionOrderRepository.save(saved);

            notificationService.sendAuctionWonNotification(
                    winner.getEmail(), auction.getTitle(), auction.getCurrentBid(), result.checkoutUrl());

        } catch (Exception e) {
            log.error("Checkout creation failed for auction '{}': {}", auction.getSlug(), e.getMessage(), e);
            saved.setStatus(AuctionOrderStatus.FAILED);
            saved.setFailureReason(e.getMessage());
            auctionOrderRepository.save(saved);

            try {
                notificationService.sendAuctionWonNotification(
                        winner.getEmail(), auction.getTitle(), auction.getCurrentBid(), null);
            } catch (Exception ne) {
                log.error("Notification also failed for '{}': {}", auction.getSlug(), ne.getMessage());
            }
        }
    }
}
