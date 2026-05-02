package com.aetherterra.payment;

import com.aetherterra.users.User;

/**
 * Abstracts payment-method qualification: creating customers, collecting card details via
 * SetupIntent, and confirming readiness. Stripe-backed in production; mock in local/test.
 */
public interface PaymentQualificationProvider {

    /**
     * Creates a SetupIntent for the given user (creating a Stripe customer first if needed).
     * Returns the client secret the frontend passes to Stripe.js.
     */
    SetupIntentResult createSetupIntent(User user);

    /**
     * Processes an inbound webhook payload. Validates signature (real Stripe) or parses
     * a simplified JSON format (mock). Updates the user record on setup_intent.succeeded.
     *
     * @param payload   raw request body as a UTF-8 string
     * @param sigHeader value of the Stripe-Signature header (ignored by mock)
     */
    void processWebhookPayload(String payload, String sigHeader);

    /** Returns true if the user has a confirmed payment method on file. */
    boolean isPaymentMethodReady(User user);
}
