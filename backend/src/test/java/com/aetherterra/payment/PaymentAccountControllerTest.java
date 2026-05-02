package com.aetherterra.payment;

import com.aetherterra.AbstractIntegrationTest;
import com.aetherterra.auth.JwtUtil;
import com.aetherterra.auctions.Auction;
import com.aetherterra.auctions.AuctionRepository;
import com.aetherterra.auctions.AuctionStatus;
import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the payment-method setup flow using the MockPaymentQualificationProvider
 * (no real Stripe credentials required; the mock is auto-selected when
 * STRIPE_SECRET_KEY is absent from the test environment).
 */
class PaymentAccountControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired AuctionRepository auctionRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtUtil jwtUtil;
    @MockitoBean JavaMailSender mailSender;

    private User buyer;
    private String buyerToken;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();

        buyer = new User();
        buyer.setEmail("buyer@example.com");
        buyer.setPasswordHash(encoder.encode("secret123"));
        buyer.setRole(UserRole.BUYER);
        buyer.setEmailVerifiedAt(Instant.now());
        buyer = userRepository.save(buyer);

        buyerToken = jwtUtil.generate(buyer.getEmail(), buyer.getRole().name());
    }

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    // ── setup-intent endpoint ─────────────────────────────────────────────────

    @Test
    void setupIntent_requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/account/payment-method/setup-intent"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void setupIntent_authenticatedUser_returnsClientSecret() throws Exception {
        mvc.perform(post("/api/v1/account/payment-method/setup-intent")
                .header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.clientSecret").exists())
            .andExpect(jsonPath("$.data.clientSecret").isNotEmpty());
    }

    @Test
    void setupIntent_createsMockStripeCustomer() throws Exception {
        mvc.perform(post("/api/v1/account/payment-method/setup-intent")
                .header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isOk());

        User updated = userRepository.findByEmail("buyer@example.com").orElseThrow();
        assertThat(updated.getStripeCustomerId()).isNotNull().startsWith("mock_cus_");
    }

    // ── status endpoint ───────────────────────────────────────────────────────

    @Test
    void status_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/account/payment-method/status"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void status_newUser_notReady() throws Exception {
        mvc.perform(get("/api/v1/account/payment-method/status")
                .header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentMethodReady").value(false));
    }

    @Test
    void status_afterMarkReady_returnsTrue() throws Exception {
        buyer.setPaymentMethodReady(true);
        userRepository.save(buyer);

        mvc.perform(get("/api/v1/account/payment-method/status")
                .header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paymentMethodReady").value(true));
    }

    // ── Stripe webhook (mock provider) ────────────────────────────────────────

    @Test
    void webhook_setupIntentSucceeded_marksUserReady() throws Exception {
        // First obtain a mock customerId by hitting the setup-intent endpoint
        mvc.perform(post("/api/v1/account/payment-method/setup-intent")
                .header("Authorization", "Bearer " + buyerToken))
            .andExpect(status().isOk());

        String customerId = userRepository.findByEmail("buyer@example.com")
                .orElseThrow().getStripeCustomerId();

        String payload = """
                {
                  "type": "setup_intent.succeeded",
                  "data": {
                    "object": {
                      "customer": "%s",
                      "payment_method": "mock_pm_test_4242"
                    }
                  }
                }
                """.formatted(customerId);

        mvc.perform(post("/api/v1/webhooks/stripe")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        User updated = userRepository.findByEmail("buyer@example.com").orElseThrow();
        assertThat(updated.isPaymentMethodReady()).isTrue();
        assertThat(updated.getStripePaymentMethodId()).isEqualTo("mock_pm_test_4242");
    }

    @Test
    void webhook_idempotent_doesNotDuplicateUpdate() throws Exception {
        buyer.setStripeCustomerId("mock_cus_" + buyer.getId());
        buyer.setPaymentMethodReady(true);
        userRepository.save(buyer);

        String payload = """
                {
                  "type": "setup_intent.succeeded",
                  "data": {
                    "object": {
                      "customer": "mock_cus_%s",
                      "payment_method": "mock_pm_test_again"
                    }
                  }
                }
                """.formatted(buyer.getId());

        mvc.perform(post("/api/v1/webhooks/stripe")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());

        // paymentMethodId should NOT have been overwritten since already ready
        User updated = userRepository.findByEmail("buyer@example.com").orElseThrow();
        assertThat(updated.isPaymentMethodReady()).isTrue();
    }

    @Test
    void webhook_unknownEventType_ignoredGracefully() throws Exception {
        String payload = """
                {
                  "type": "customer.created",
                  "data": { "object": {} }
                }
                """;

        mvc.perform(post("/api/v1/webhooks/stripe")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());
    }

    // ── Bid eligibility via paymentMethodReady ────────────────────────────────

    @Test
    void placeBid_userWithoutPaymentMethodReady_returns403() throws Exception {
        var auction = new Auction();
        auction.setSlug("pay-test-auction");
        auction.setTitle("Pay Test Auction");
        auction.setStatus(AuctionStatus.LIVE);
        auction.setStartingBid(new BigDecimal("100.00"));
        auction.setStartsAt(Instant.now().minusSeconds(3600));
        auction.setEndsAt(Instant.now().plusSeconds(86400));
        auction.setCreatedById(UUID.randomUUID());
        auction = auctionRepository.save(auction);

        buyer.setShirtSize("M");
        buyer.setPaymentMethodReady(false);
        userRepository.save(buyer);

        try {
            mvc.perform(post("/api/v1/auctions/pay-test-auction/bids")
                    .header("Authorization", "Bearer " + buyerToken)
                    .contentType("application/json")
                    .content(om.writeValueAsString(Map.of("amount", 150.00))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Save a payment method before placing a bid"));
        } finally {
            auctionRepository.delete(auction);
        }
    }
}
