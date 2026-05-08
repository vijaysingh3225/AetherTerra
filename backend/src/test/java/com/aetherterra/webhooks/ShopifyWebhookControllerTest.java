package com.aetherterra.webhooks;

import com.aetherterra.AbstractIntegrationTest;
import com.aetherterra.auctions.Auction;
import com.aetherterra.auctions.AuctionRepository;
import com.aetherterra.auctions.AuctionScheduler;
import com.aetherterra.auctions.AuctionStatus;
import com.aetherterra.orders.AuctionOrder;
import com.aetherterra.orders.AuctionOrderRepository;
import com.aetherterra.orders.AuctionOrderStatus;
import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Shopify webhook receiver.
 *
 * A fixed test secret is injected so we can exercise both valid and invalid HMAC paths.
 * Orders are created directly in the DB (no scheduler) to keep tests fast and focused.
 */
@TestPropertySource(properties = "aetherterra.shopify.webhook-secret=test-whsec-1234567890abcdef")
class ShopifyWebhookControllerTest extends AbstractIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-whsec-1234567890abcdef";

    @Autowired MockMvc mvc;
    @Autowired AuctionOrderRepository orderRepo;
    @Autowired AuctionRepository auctionRepo;
    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired ProcessedShopifyWebhookRepository processedWebhookRepo;
    @Autowired AuctionScheduler auctionScheduler;
    @MockitoBean JavaMailSender mailSender;

    private UUID auctionId;
    private AuctionOrder pendingOrder;

    @BeforeEach
    void setup() {
        processedWebhookRepo.deleteAll();
        orderRepo.deleteAll();
        auctionRepo.deleteAll();

        User buyer = userRepo.findByEmail("buyer-wh-test@example.com").orElseGet(User::new);
        buyer.setEmail("buyer-wh-test@example.com");
        buyer.setPasswordHash(encoder.encode("secret"));
        buyer.setRole(UserRole.BUYER);
        buyer.setEmailVerifiedAt(Instant.now());
        buyer = userRepo.save(buyer);

        Auction auction = new Auction();
        auction.setSlug("shopify-webhook-test");
        auction.setTitle("Shopify Webhook Test Auction");
        auction.setStatus(AuctionStatus.ENDED);
        auction.setStartingBid(new BigDecimal("100.00"));
        auction.setStartsAt(Instant.now().minus(2, ChronoUnit.HOURS));
        auction.setEndsAt(Instant.now().minus(1, ChronoUnit.HOURS));
        auction.setCreatedById(buyer.getId());
        auction = auctionRepo.save(auction);
        auctionId = auction.getId();

        AuctionOrder order = new AuctionOrder();
        order.setAuctionId(auctionId);
        order.setUserId(buyer.getId());
        order.setAmount(new BigDecimal("150.00"));
        order.setProvider("SHOPIFY");
        order.setProviderOrderId("gid://shopify/DraftOrder/99999");
        order.setCheckoutUrl("https://test.myshopify.com/draft_orders/test/invoice");
        order.setStatus(AuctionOrderStatus.PENDING_PAYMENT);
        order.setPaymentDueAt(Instant.now().plus(24, ChronoUnit.HOURS));
        pendingOrder = orderRepo.save(order);
    }

    @AfterEach
    void cleanup() {
        processedWebhookRepo.deleteAll();
        orderRepo.deleteAll();
        auctionRepo.deleteAll();
    }

    // ── Signature verification ─────────────────────────────────────────────────

    @Test
    void validSignature_marksOrderPaid() throws Exception {
        String payload = buildPayload(pendingOrder.getId(), auctionId, 55001L);
        String hmac = computeHmac(payload);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", hmac)
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        AuctionOrder updated = orderRepo.findById(pendingOrder.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AuctionOrderStatus.PAID);
        assertThat(updated.getPaidAt()).isNotNull();
    }

    @Test
    void invalidSignature_returns401_andOrderUntouched() throws Exception {
        String payload = buildPayload(pendingOrder.getId(), auctionId, 55002L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", "dGhpcyBpcyBub3QgdGhlIHJpZ2h0IHNpZ25hdHVyZQ==")
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isUnauthorized());

        AuctionOrder untouched = orderRepo.findById(pendingOrder.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(AuctionOrderStatus.PENDING_PAYMENT);
    }

    @Test
    void missingSignatureHeader_returns401() throws Exception {
        String payload = buildPayload(pendingOrder.getId(), auctionId, 55003L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isUnauthorized());
    }

    // ── Order matching — primary: auction_order_id ─────────────────────────────

    @Test
    void matchByAuctionOrderId_marksOrderPaid() throws Exception {
        String payload = buildPayload(pendingOrder.getId(), auctionId, 55010L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        assertThat(orderRepo.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionOrderStatus.PAID);
    }

    @Test
    void matchByAuctionOrderId_unknownId_returnsOkAndLogs() throws Exception {
        // auction_order_id points to a UUID that does not exist; fallback auction_id also absent
        String payload = buildPayloadOrderIdOnly(UUID.randomUUID(), 55011L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok")); // 200 so Shopify doesn't retry

        // Our order is untouched
        assertThat(orderRepo.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionOrderStatus.PENDING_PAYMENT);
    }

    // ── Order matching — fallback: auction_id only ─────────────────────────────

    @Test
    void fallback_matchByAuctionId_whenOrderIdAbsent_marksOrderPaid() throws Exception {
        // Payload has auction_id but no auction_order_id (simulates pre-v9 order or Shopify
        // orders created before the metadata was added to the Draft Order)
        String payload = buildPayloadAuctionIdOnly(auctionId, 55020L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        assertThat(orderRepo.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionOrderStatus.PAID);
    }

    @Test
    void fallback_unknownAuctionId_returnsOkAndLogs() throws Exception {
        String payload = buildPayloadAuctionIdOnly(UUID.randomUUID(), 55021L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok")); // 200 — not a Shopify error

        assertThat(orderRepo.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionOrderStatus.PENDING_PAYMENT);
    }

    @Test
    void noMatchingAttribute_returnsOkAndLogs() throws Exception {
        // Payload carries no auction_order_id and no auction_id
        String payload = """
                {"id": 99999, "note_attributes": [{"name": "source", "value": "aetherterra_auction"}]}
                """;

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    // ── Status transitions ─────────────────────────────────────────────────────

    @Test
    void alreadyPaidOrder_isIdempotent() throws Exception {
        pendingOrder.setStatus(AuctionOrderStatus.PAID);
        pendingOrder.setPaidAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        orderRepo.save(pendingOrder);

        String payload = buildPayload(pendingOrder.getId(), auctionId, 55030L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        AuctionOrder unchanged = orderRepo.findById(pendingOrder.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(AuctionOrderStatus.PAID);
    }

    @Test
    void expiredOrder_becomesPaid_onLateWebhook() throws Exception {
        // Winner paid after the deadline — the order was expired by the scheduler,
        // but Shopify still fires orders/paid. We accept late payment.
        Instant expiredAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        pendingOrder.setStatus(AuctionOrderStatus.EXPIRED);
        pendingOrder.setExpiredAt(expiredAt);
        pendingOrder.setFailureReason("Payment deadline exceeded");
        pendingOrder.setPaymentDueAt(Instant.now().minus(1, ChronoUnit.HOURS));
        orderRepo.save(pendingOrder);

        String payload = buildPayload(pendingOrder.getId(), auctionId, 55031L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        AuctionOrder updated = orderRepo.findById(pendingOrder.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AuctionOrderStatus.PAID);
        assertThat(updated.getPaidAt()).isNotNull();
        // expiredAt is preserved so admin can see the order was late
        assertThat(updated.getExpiredAt()).isEqualTo(expiredAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    }

    @Test
    void failedOrder_isNotTransitionedToPaid() throws Exception {
        pendingOrder.setStatus(AuctionOrderStatus.FAILED);
        pendingOrder.setFailureReason("Shopify API error");
        orderRepo.save(pendingOrder);

        String payload = buildPayload(pendingOrder.getId(), auctionId, 55032L);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        // FAILED orders are not automatically recovered
        assertThat(orderRepo.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionOrderStatus.FAILED);
    }

    // ── Idempotency ────────────────────────────────────────────────────────────

    @Test
    void duplicateWebhookId_doesNotDoubleProcess() throws Exception {
        String webhookId = UUID.randomUUID().toString();
        String payload = buildPayload(pendingOrder.getId(), auctionId, 55040L);
        String hmac = computeHmac(payload);

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", hmac)
                .header("X-Shopify-Webhook-Id", webhookId)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "orders/paid")
                .header("X-Shopify-Hmac-Sha256", hmac)
                .header("X-Shopify-Webhook-Id", webhookId)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("already_processed"));

        assertThat(processedWebhookRepo.count()).isEqualTo(1);
        assertThat(orderRepo.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionOrderStatus.PAID);
    }

    // ── Payment deadline expiry ────────────────────────────────────────────────

    @Test
    void expiredDeadline_marksPendingOrderExpired() {
        pendingOrder.setPaymentDueAt(Instant.now().minus(1, ChronoUnit.HOURS));
        orderRepo.save(pendingOrder);

        auctionScheduler.expireOverduePayments();

        AuctionOrder expired = orderRepo.findById(pendingOrder.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(AuctionOrderStatus.EXPIRED);
        assertThat(expired.getExpiredAt()).isNotNull();
        assertThat(expired.getFailureReason()).contains("deadline");
    }

    @Test
    void futureDeadline_doesNotExpireOrder() {
        // payment_due_at is 24h in the future (set in setup)
        auctionScheduler.expireOverduePayments();

        AuctionOrder stillPending = orderRepo.findById(pendingOrder.getId()).orElseThrow();
        assertThat(stillPending.getStatus()).isEqualTo(AuctionOrderStatus.PENDING_PAYMENT);
        assertThat(stillPending.getExpiredAt()).isNull();
    }

    @Test
    void paidOrder_isNeverExpiredByScheduler() {
        pendingOrder.setStatus(AuctionOrderStatus.PAID);
        pendingOrder.setPaidAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        pendingOrder.setPaymentDueAt(Instant.now().minus(1, ChronoUnit.HOURS));
        orderRepo.save(pendingOrder);

        auctionScheduler.expireOverduePayments();

        assertThat(orderRepo.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionOrderStatus.PAID);
    }

    // ── Unrelated topic is safely ignored ─────────────────────────────────────

    @Test
    void unknownTopic_returnsOkAndDoesNothing() throws Exception {
        String payload = "{}";

        mvc.perform(post("/api/v1/webhooks/shopify")
                .contentType("application/json")
                .header("X-Shopify-Topic", "products/create")
                .header("X-Shopify-Hmac-Sha256", computeHmac(payload))
                .header("X-Shopify-Webhook-Id", UUID.randomUUID().toString())
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));

        assertThat(orderRepo.findById(pendingOrder.getId()).orElseThrow().getStatus())
                .isEqualTo(AuctionOrderStatus.PENDING_PAYMENT);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Full payload with both auction_order_id (primary) and auction_id (fallback). */
    private String buildPayload(UUID auctionOrderId, UUID auctionId, long shopifyOrderId) {
        return """
                {
                  "id": %d,
                  "financial_status": "paid",
                  "note_attributes": [
                    {"name": "auction_order_id", "value": "%s"},
                    {"name": "auction_id",       "value": "%s"},
                    {"name": "source",           "value": "aetherterra_auction"}
                  ]
                }
                """.formatted(shopifyOrderId, auctionOrderId, auctionId);
    }

    /** Payload with only auction_order_id — no auction_id fallback. */
    private String buildPayloadOrderIdOnly(UUID auctionOrderId, long shopifyOrderId) {
        return """
                {
                  "id": %d,
                  "financial_status": "paid",
                  "note_attributes": [
                    {"name": "auction_order_id", "value": "%s"},
                    {"name": "source",           "value": "aetherterra_auction"}
                  ]
                }
                """.formatted(shopifyOrderId, auctionOrderId);
    }

    /** Payload with only auction_id — simulates orders created before metadata was added. */
    private String buildPayloadAuctionIdOnly(UUID auctionId, long shopifyOrderId) {
        return """
                {
                  "id": %d,
                  "financial_status": "paid",
                  "note_attributes": [
                    {"name": "auction_id", "value": "%s"},
                    {"name": "source",     "value": "aetherterra_auction"}
                  ]
                }
                """.formatted(shopifyOrderId, auctionId);
    }

    private String computeHmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
