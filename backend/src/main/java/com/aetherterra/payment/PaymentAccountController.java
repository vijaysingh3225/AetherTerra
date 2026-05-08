package com.aetherterra.payment;

import com.aetherterra.common.ApiResponse;
import com.aetherterra.users.User;
import com.aetherterra.users.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/account/payment-method")
public class PaymentAccountController {

    private final PaymentQualificationProvider paymentProvider;
    private final UserRepository userRepository;

    public PaymentAccountController(PaymentQualificationProvider paymentProvider,
                                    UserRepository userRepository) {
        this.paymentProvider = paymentProvider;
        this.userRepository = userRepository;
    }

    /**
     * Creates a Stripe SetupIntent (or mock equivalent) so the frontend can collect
     * card details. Returns the clientSecret the frontend passes to Stripe.js.
     */
    @PostMapping("/setup-intent")
    public ResponseEntity<?> createSetupIntent(Authentication authentication) {
        User user = resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.message("Unauthorized"));
        }
        SetupIntentResult result = paymentProvider.createSetupIntent(user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "clientSecret", result.clientSecret(),
                "customerId", result.customerId()
        )));
    }

    /** Returns whether the authenticated user has a confirmed payment method on file. */
    @GetMapping("/status")
    public ResponseEntity<?> paymentStatus(Authentication authentication) {
        User user = resolveUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.message("Unauthorized"));
        }
        boolean ready = paymentProvider.isPaymentMethodReady(user);
        // Map.of() rejects null values; use HashMap to allow paymentMethodAddedAt=null
        var body = new java.util.HashMap<String, Object>();
        body.put("paymentMethodReady", ready);
        body.put("paymentMethodAddedAt", user.getPaymentMethodAddedAt());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }
}
