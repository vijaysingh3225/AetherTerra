package com.aetherterra.orders;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auction_orders")
public class AuctionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "auction_id", nullable = false, unique = true)
    private UUID auctionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "winning_bid_id")
    private UUID winningBidId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency = "USD";

    @Column(name = "shirt_size", length = 10)
    private String shirtSize;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuctionOrderStatus status = AuctionOrderStatus.PENDING_PAYMENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAuctionId() { return auctionId; }
    public void setAuctionId(UUID auctionId) { this.auctionId = auctionId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getWinningBidId() { return winningBidId; }
    public void setWinningBidId(UUID winningBidId) { this.winningBidId = winningBidId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getShirtSize() { return shirtSize; }
    public void setShirtSize(String shirtSize) { this.shirtSize = shirtSize; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderOrderId() { return providerOrderId; }
    public void setProviderOrderId(String providerOrderId) { this.providerOrderId = providerOrderId; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public AuctionOrderStatus getStatus() { return status; }
    public void setStatus(AuctionOrderStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
