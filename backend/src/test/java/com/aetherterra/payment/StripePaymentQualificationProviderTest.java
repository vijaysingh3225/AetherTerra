package com.aetherterra.payment;

import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import com.aetherterra.users.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StripePaymentQualificationProvider webhook processing.
 * No Spring context — exercises signature logic and state transitions directly.
 */
@ExtendWith(MockitoExtension.class)
class StripePaymentQualificationProviderTest {

    @Mock
    UserRepository userRepository;

    private static final String PAYLOAD = """
            {"type":"setup_intent.succeeded","data":{"object":{"customer":"cus_test123","payment_method":"pm_test_4242"}}}
            """;

    @Test
    void invalidSignature_throwsSecurityException() {
        var provider = new StripePaymentQualificationProvider(
                "sk_test_fake", "whsec_abcdefghij1234567890abcdefghij12", userRepository);

        assertThatThrownBy(() -> provider.processWebhookPayload(PAYLOAD, "v1=invalidsignature"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid webhook signature");
    }

    @Test
    void nullSignatureHeader_withActiveSecret_throwsSecurityException() {
        var provider = new StripePaymentQualificationProvider(
                "sk_test_fake", "whsec_abcdefghij1234567890abcdefghij12", userRepository);

        assertThatThrownBy(() -> provider.processWebhookPayload(PAYLOAD, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void blankWebhookSecret_skipsVerification_marksUserReady() {
        var provider = new StripePaymentQualificationProvider("sk_test_fake", "", userRepository);

        User user = new User();
        user.setEmail("test@example.com");
        user.setRole(UserRole.BUYER);
        user.setStripeCustomerId("cus_test123");

        when(userRepository.findByStripeCustomerId("cus_test123")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // Should not throw even with null signature header
        provider.processWebhookPayload(PAYLOAD, null);

        assertThat(user.isPaymentMethodReady()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void blankWebhookSecret_unknownEventType_ignoredGracefully() {
        var provider = new StripePaymentQualificationProvider("sk_test_fake", "", userRepository);

        String payload = """
                {"type":"customer.created","data":{"object":{}}}
                """;

        // Should not throw and should not interact with the repository
        provider.processWebhookPayload(payload, null);
    }

    @Test
    void blankWebhookSecret_alreadyReadyUser_notUpdatedAgain() {
        var provider = new StripePaymentQualificationProvider("sk_test_fake", "", userRepository);

        User user = new User();
        user.setEmail("already@example.com");
        user.setRole(UserRole.BUYER);
        user.setStripeCustomerId("cus_test123");
        user.setPaymentMethodReady(true);

        when(userRepository.findByStripeCustomerId("cus_test123")).thenReturn(Optional.of(user));

        provider.processWebhookPayload(PAYLOAD, null);

        // save should NOT be called — already ready, no state change needed
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }
}
