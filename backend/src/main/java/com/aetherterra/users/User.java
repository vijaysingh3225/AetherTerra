package com.aetherterra.users;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "shirt_size")
    private String shirtSize;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_payment_method_id")
    private String stripePaymentMethodId;

    @Column(name = "payment_method_ready", nullable = false)
    private boolean paymentMethodReady = false;

    @Column(name = "payment_method_added_at")
    private Instant paymentMethodAddedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.BUYER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public User() {}

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Called after Stripe confirms a payment method has been saved. */
    public void markPaymentMethodReady(String stripePaymentMethodId) {
        this.stripePaymentMethodId = stripePaymentMethodId;
        this.paymentMethodReady = true;
        this.paymentMethodAddedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getShirtSize() { return shirtSize; }
    public void setShirtSize(String shirtSize) { this.shirtSize = shirtSize; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public String getStripePaymentMethodId() { return stripePaymentMethodId; }
    public boolean isPaymentMethodReady() { return paymentMethodReady; }
    public void setPaymentMethodReady(boolean paymentMethodReady) { this.paymentMethodReady = paymentMethodReady; }
    public Instant getPaymentMethodAddedAt() { return paymentMethodAddedAt; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public boolean hasShirtSize() { return shirtSize != null && !shirtSize.isBlank(); }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
