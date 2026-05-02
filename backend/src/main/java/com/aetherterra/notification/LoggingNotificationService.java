package com.aetherterra.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/** Fallback — logs notifications instead of sending emails. Used when mail is not configured. */
public class LoggingNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

    public LoggingNotificationService() {
        log.warn("NotificationService: LOGGING mode — no emails will be sent");
    }

    @Override
    public void sendAuctionWonNotification(String winnerEmail, String auctionTitle,
                                            BigDecimal winningBid, String checkoutUrl) {
        log.info("[NOTIFICATION] Auction won — to: {}  auction: {}  bid: {}  checkout: {}",
                winnerEmail, auctionTitle, winningBid, checkoutUrl);
    }
}
