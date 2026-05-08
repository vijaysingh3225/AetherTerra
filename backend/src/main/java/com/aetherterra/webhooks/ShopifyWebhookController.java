package com.aetherterra.webhooks;

import com.aetherterra.orders.AuctionOrder;
import com.aetherterra.orders.AuctionOrderRepository;
import com.aetherterra.orders.AuctionOrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Receives and validates Shopify webhook events.
 *
 * Supported topics:
 *   orders/paid — fires when a Shopify Order (converted from a Draft Order invoice) is paid.
 *                 Marks the corresponding AuctionOrder PAID.
 *
 * Authentication: HMAC-SHA256 over the raw body using SHOPIFY_WEBHOOK_SECRET.
 * When SHOPIFY_WEBHOOK_SECRET is blank (local/mock mode), signature verification is skipped.
 *
 * Order matching (orders/paid):
 *   Primary  — auction_order_id note_attribute (our AuctionOrder UUID, always present from v9+)
 *   Fallback — auction_id note_attribute (the Auction UUID; covers any pre-v9 orders)
 *
 * Status transitions allowed:
 *   PENDING_PAYMENT → PAID   normal case
 *   EXPIRED → PAID           late payment — winner paid after the deadline; we still honour it
 *   PAID → (no-op)           idempotent; already processed
 *   FAILED/CANCELLED → PAID  skipped; no automatic recovery from terminal states
 *
 * Idempotency: every X-Shopify-Webhook-Id is recorded in processed_shopify_webhooks.
 * Shopify retries unacknowledged deliveries up to 19 times; duplicates are silently ignored.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class ShopifyWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ShopifyWebhookController.class);

    private final String webhookSecret;
    private final AuctionOrderRepository auctionOrderRepository;
    private final ProcessedShopifyWebhookRepository processedWebhookRepository;
    private final ObjectMapper objectMapper;

    public ShopifyWebhookController(
            @Value("${aetherterra.shopify.webhook-secret:}") String webhookSecret,
            AuctionOrderRepository auctionOrderRepository,
            ProcessedShopifyWebhookRepository processedWebhookRepository,
            ObjectMapper objectMapper) {
        this.webhookSecret = webhookSecret;
        this.auctionOrderRepository = auctionOrderRepository;
        this.processedWebhookRepository = processedWebhookRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/shopify")
    public ResponseEntity<?> shopifyWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Shopify-Hmac-Sha256", required = false) String hmacHeader,
            @RequestHeader(value = "X-Shopify-Topic", required = false) String topic,
            @RequestHeader(value = "X-Shopify-Webhook-Id", required = false) String webhookId) {

        String payload;
        try {
            payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to read Shopify webhook body: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot read body"));
        }

        if (!verifySignature(payload, hmacHeader)) {
            log.warn("Shopify webhook rejected: invalid HMAC signature for topic={}", topic);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
        }

        log.info("Shopify webhook received: topic={} webhookId={}", topic, webhookId);

        // Idempotency: skip if this exact delivery has already been processed
        if (webhookId != null && !webhookId.isBlank() && processedWebhookRepository.existsById(webhookId)) {
            log.info("Shopify webhook {} already processed; skipping", webhookId);
            return ResponseEntity.ok(Map.of("status", "already_processed"));
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            handleEvent(topic, root);
        } catch (Exception e) {
            log.error("Shopify webhook processing error for topic={}: {}", topic, e.getMessage(), e);
            // Return 200 so Shopify does not retry on application-level errors
            return ResponseEntity.ok(Map.of("status", "error_logged"));
        }

        if (webhookId != null && !webhookId.isBlank()) {
            try {
                processedWebhookRepository.save(
                        new ProcessedShopifyWebhook(webhookId, topic != null ? topic : "unknown"));
            } catch (Exception e) {
                // A concurrent duplicate may cause a unique-constraint violation — that's fine
                log.debug("Could not record processed webhook {}: {}", webhookId, e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ── Event routing ──────────────────────────────────────────────────────────

    private void handleEvent(String topic, JsonNode root) {
        if ("orders/paid".equals(topic)) {
            handleOrderPaid(root);
        } else {
            log.debug("Unhandled Shopify webhook topic: {}", topic);
        }
    }

    /**
     * Handles orders/paid. When a Draft Order invoice is paid, Shopify converts it to an Order
     * and fires this event. The Order inherits the Draft Order's custom_attributes as
     * note_attributes, which include auction_order_id (primary) and auction_id (fallback).
     *
     * EXPIRED → PAID is intentionally allowed: if a winner pays after the deadline,
     * we still mark the order PAID. The expired_at timestamp is preserved so admins
     * can see that payment arrived late. The admin decides what to do next; no automatic
     * second-chance offer runs in v1.
     */
    private void handleOrderPaid(JsonNode root) {
        long shopifyOrderId = root.path("id").asLong(0);

        Optional<AuctionOrder> orderOpt = findOrderForPaidWebhook(root, shopifyOrderId);
        if (orderOpt.isEmpty()) {
            return;
        }

        AuctionOrder order = orderOpt.get();

        if (order.getStatus() == AuctionOrderStatus.PAID) {
            log.info("orders/paid: AuctionOrder {} is already PAID; no-op", order.getId());
            return;
        }

        if (order.getStatus() == AuctionOrderStatus.EXPIRED) {
            // Late payment: winner paid after the expiry deadline.
            // We still accept it and mark PAID; expired_at remains for admin visibility.
            log.warn("orders/paid: AuctionOrder {} was EXPIRED but Shopify order {} is paid — " +
                    "marking PAID (late payment). expired_at={} paidAt=now",
                    order.getId(), shopifyOrderId, order.getExpiredAt());
            order.setStatus(AuctionOrderStatus.PAID);
            order.setPaidAt(Instant.now());
            auctionOrderRepository.save(order);
            return;
        }

        if (order.getStatus() != AuctionOrderStatus.PENDING_PAYMENT) {
            log.warn("orders/paid: AuctionOrder {} has terminal status {}; skipping transition to PAID",
                    order.getId(), order.getStatus());
            return;
        }

        order.setStatus(AuctionOrderStatus.PAID);
        order.setPaidAt(Instant.now());
        auctionOrderRepository.save(order);

        log.info("AuctionOrder {} marked PAID via Shopify order {} for auction {}",
                order.getId(), shopifyOrderId, order.getAuctionId());
    }

    /**
     * Resolves the AuctionOrder for an orders/paid event.
     *
     * Strategy:
     * 1. auction_order_id note_attribute → findById (direct, unambiguous)
     * 2. auction_id note_attribute → findByAuctionId (fallback for pre-metadata orders)
     *
     * Returns empty if no order can be found; logs a warning in that case.
     */
    private Optional<AuctionOrder> findOrderForPaidWebhook(JsonNode root, long shopifyOrderId) {
        String orderIdStr = findNoteAttribute(root, "auction_order_id");
        if (orderIdStr != null) {
            try {
                UUID orderId = UUID.fromString(orderIdStr);
                Optional<AuctionOrder> found = auctionOrderRepository.findById(orderId);
                if (found.isPresent()) {
                    log.debug("orders/paid Shopify#{}: matched AuctionOrder {} via auction_order_id",
                            shopifyOrderId, orderId);
                    return found;
                }
                log.warn("orders/paid Shopify#{}: auction_order_id={} not found in DB; trying auction_id fallback",
                        shopifyOrderId, orderId);
            } catch (IllegalArgumentException e) {
                log.warn("orders/paid Shopify#{}: invalid auction_order_id '{}'; trying auction_id fallback",
                        shopifyOrderId, orderIdStr);
            }
        }

        String auctionIdStr = findNoteAttribute(root, "auction_id");
        if (auctionIdStr == null) {
            log.warn("orders/paid Shopify#{}: no auction_order_id or auction_id in note_attributes; cannot match order",
                    shopifyOrderId);
            return Optional.empty();
        }
        try {
            UUID auctionId = UUID.fromString(auctionIdStr);
            Optional<AuctionOrder> found = auctionOrderRepository.findByAuctionId(auctionId);
            if (found.isEmpty()) {
                log.warn("orders/paid Shopify#{}: no AuctionOrder found for auction_id={}", shopifyOrderId, auctionId);
            } else {
                log.debug("orders/paid Shopify#{}: matched AuctionOrder {} via auction_id fallback",
                        shopifyOrderId, found.get().getId());
            }
            return found;
        } catch (IllegalArgumentException e) {
            log.warn("orders/paid Shopify#{}: invalid auction_id '{}'; cannot match order", shopifyOrderId, auctionIdStr);
            return Optional.empty();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String findNoteAttribute(JsonNode root, String key) {
        JsonNode attrs = root.path("note_attributes");
        if (!attrs.isArray()) return null;
        for (JsonNode attr : attrs) {
            if (key.equals(attr.path("name").asText(null))) {
                return attr.path("value").asText(null);
            }
        }
        return null;
    }

    /**
     * Verifies the X-Shopify-Hmac-Sha256 header against HMAC-SHA256(secret, rawBody).
     * When SHOPIFY_WEBHOOK_SECRET is blank, verification is skipped and a warning is logged
     * (mock/local-dev mode — do not use in production without setting the secret).
     */
    boolean verifySignature(String payload, String hmacHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("SHOPIFY_WEBHOOK_SECRET not configured — skipping signature verification (mock/dev mode only)");
            return true;
        }
        if (hmacHeader == null || hmacHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(digest);
            return computed.equals(hmacHeader);
        } catch (Exception e) {
            log.error("HMAC verification error: {}", e.getMessage());
            return false;
        }
    }
}
