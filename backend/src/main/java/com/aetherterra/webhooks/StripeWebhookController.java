package com.aetherterra.webhooks;

import com.aetherterra.payment.PaymentQualificationProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentQualificationProvider paymentProvider;

    public StripeWebhookController(PaymentQualificationProvider paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    /**
     * Receives Stripe webhook events. The raw body must be read before any JSON
     * deserialization so Stripe's signature check can verify it against the
     * Stripe-Signature header.
     */
    @PostMapping("/stripe")
    public ResponseEntity<?> stripeWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        String payload;
        try {
            payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to read Stripe webhook body: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot read body"));
        }

        try {
            paymentProvider.processWebhookPayload(payload, sigHeader);
        } catch (SecurityException e) {
            log.warn("Stripe webhook rejected: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", "Invalid signature"));
        } catch (Exception e) {
            log.error("Stripe webhook processing error: {}", e.getMessage(), e);
            // Return 200 so Stripe does not retry on application errors
            return ResponseEntity.ok(Map.of("status", "error_logged"));
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
