package com.aetherterra.payment;

import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StripePaymentQualificationProvider implements PaymentQualificationProvider {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentQualificationProvider.class);

    private final String secretKey;
    private final String webhookSecret;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StripePaymentQualificationProvider(String secretKey, String webhookSecret,
                                               UserRepository userRepository) {
        this.secretKey = secretKey;
        this.webhookSecret = webhookSecret;
        this.userRepository = userRepository;
    }

    @Override
    public SetupIntentResult createSetupIntent(User user) {
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(secretKey).build();

            String customerId = user.getStripeCustomerId();
            if (customerId == null) {
                var params = CustomerCreateParams.builder()
                        .setEmail(user.getEmail())
                        .putMetadata("aetherterra_user_id", user.getId().toString())
                        .build();
                Customer customer = Customer.create(params, options);
                customerId = customer.getId();
                user.setStripeCustomerId(customerId);
                userRepository.save(user);
                log.info("Created Stripe customer {} for user {}", customerId, user.getEmail());
            }

            var siParams = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .build();
            SetupIntent si = SetupIntent.create(siParams, options);
            return new SetupIntentResult(si.getClientSecret(), customerId);
        } catch (StripeException e) {
            log.error("Stripe error creating SetupIntent for {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Failed to create payment setup: " + e.getMessage(), e);
        }
    }

    @Override
    public void processWebhookPayload(String payload, String sigHeader) {
        com.stripe.model.Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature");
            throw new SecurityException("Invalid webhook signature");
        }

        if (!"setup_intent.succeeded".equals(event.getType())) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode obj = root.path("data").path("object");
            String customerId = obj.path("customer").asText(null);
            String paymentMethodId = obj.path("payment_method").asText(null);

            if (customerId == null || paymentMethodId == null) {
                log.warn("setup_intent.succeeded missing customer or payment_method");
                return;
            }

            userRepository.findByStripeCustomerId(customerId).ifPresentOrElse(user -> {
                if (user.isPaymentMethodReady()) {
                    log.debug("User {} already payment-method-ready; skipping", user.getEmail());
                    return;
                }
                user.markPaymentMethodReady(paymentMethodId);
                userRepository.save(user);
                log.info("Marked user {} as payment-method-ready via webhook", user.getEmail());
            }, () -> log.warn("No user found for Stripe customer {}", customerId));

        } catch (Exception e) {
            log.error("Error processing setup_intent.succeeded webhook: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isPaymentMethodReady(User user) {
        return user.isPaymentMethodReady();
    }
}
