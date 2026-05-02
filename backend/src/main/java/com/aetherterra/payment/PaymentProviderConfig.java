package com.aetherterra.payment;

import com.aetherterra.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderConfig.class);

    @Value("${aetherterra.stripe.secret-key:}")
    private String secretKey;

    @Value("${aetherterra.stripe.webhook-secret:}")
    private String webhookSecret;

    @Bean
    public PaymentQualificationProvider paymentQualificationProvider(UserRepository userRepository) {
        if (secretKey != null && !secretKey.isBlank()) {
            log.info("PaymentQualificationProvider: Stripe (live)");
            return new StripePaymentQualificationProvider(secretKey, webhookSecret, userRepository);
        }
        return new MockPaymentQualificationProvider(userRepository);
    }
}
