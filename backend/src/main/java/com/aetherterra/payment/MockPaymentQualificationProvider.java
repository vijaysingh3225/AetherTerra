package com.aetherterra.payment;

import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Used when no Stripe credentials are configured (local dev / test).
 * <p>
 * Webhook format accepted by this provider for testing:
 * <pre>
 * {
 *   "type": "setup_intent.succeeded",
 *   "data": {
 *     "object": {
 *       "customer": "mock_cus_<user-id>",
 *       "payment_method": "mock_pm_test"
 *     }
 *   }
 * }
 * </pre>
 */
public class MockPaymentQualificationProvider implements PaymentQualificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentQualificationProvider.class);

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MockPaymentQualificationProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
        log.warn("PaymentQualificationProvider: MOCK mode — no real Stripe calls will be made");
    }

    @Override
    public SetupIntentResult createSetupIntent(User user) {
        String mockCustomerId = "mock_cus_" + user.getId();
        if (user.getStripeCustomerId() == null) {
            user.setStripeCustomerId(mockCustomerId);
            userRepository.save(user);
        }
        String mockClientSecret = "mock_seti_" + UUID.randomUUID() + "_secret_mock";
        log.info("Mock SetupIntent created for {} — clientSecret: {}", user.getEmail(), mockClientSecret);
        return new SetupIntentResult(mockClientSecret, user.getStripeCustomerId());
    }

    @Override
    public void processWebhookPayload(String payload, String sigHeader) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.path("type").asText();
            if (!"setup_intent.succeeded".equals(type)) {
                return;
            }
            JsonNode obj = root.path("data").path("object");
            String customerId = obj.path("customer").asText(null);
            String paymentMethodId = obj.path("payment_method").asText("mock_pm_test");

            if (customerId == null) {
                log.warn("Mock webhook: missing customer field");
                return;
            }

            userRepository.findByStripeCustomerId(customerId).ifPresentOrElse(user -> {
                if (user.isPaymentMethodReady()) {
                    return;
                }
                user.markPaymentMethodReady(paymentMethodId);
                userRepository.save(user);
                log.info("Mock webhook: marked {} as payment-method-ready", user.getEmail());
            }, () -> log.warn("Mock webhook: no user for customer {}", customerId));

        } catch (Exception e) {
            log.error("Mock webhook parse error: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isPaymentMethodReady(User user) {
        return user.isPaymentMethodReady();
    }
}
