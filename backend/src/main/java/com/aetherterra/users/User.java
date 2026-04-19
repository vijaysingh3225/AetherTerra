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

    @Column(name = "payment_method_brand")
    private String paymentMethodBrand;

    @Column(name = "payment_method_last4")
    private String paymentMethodLast4;

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

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getShirtSize() { return shirtSize; }
    public void setShirtSize(String shirtSize) { this.shirtSize = shirtSize; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public String getPaymentMethodBrand() { return paymentMethodBrand; }
    public void setPaymentMethodBrand(String paymentMethodBrand) { this.paymentMethodBrand = paymentMethodBrand; }
    public String getPaymentMethodLast4() { return paymentMethodLast4; }
    public void setPaymentMethodLast4(String paymentMethodLast4) { this.paymentMethodLast4 = paymentMethodLast4; }
    public Instant getPaymentMethodAddedAt() { return paymentMethodAddedAt; }
    public void setPaymentMethodAddedAt(Instant paymentMethodAddedAt) { this.paymentMethodAddedAt = paymentMethodAddedAt; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public boolean hasShirtSize() { return shirtSize != null && !shirtSize.isBlank(); }
    public boolean hasSavedPaymentMethod() {
        return paymentMethodBrand != null && !paymentMethodBrand.isBlank()
                && paymentMethodLast4 != null && !paymentMethodLast4.isBlank();
    }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
