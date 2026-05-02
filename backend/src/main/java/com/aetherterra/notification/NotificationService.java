package com.aetherterra.notification;

import java.math.BigDecimal;

/**
 * Sends outbound notifications to bidders and winners. Never throws — failures
 * are logged and surfaced in the admin dashboard but never block auction logic.
 */
public interface NotificationService {

    /**
     * Notifies the auction winner with a checkout link to pay for their item.
     *
     * @param checkoutUrl may be null if order creation failed; a fallback message is sent
     */
    void sendAuctionWonNotification(String winnerEmail, String auctionTitle,
                                    BigDecimal winningBid, String checkoutUrl);
}
