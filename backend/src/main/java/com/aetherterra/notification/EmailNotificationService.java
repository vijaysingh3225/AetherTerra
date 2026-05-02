package com.aetherterra.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class EmailNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationService(JavaMailSender mailSender, String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendAuctionWonNotification(String winnerEmail, String auctionTitle,
                                            BigDecimal winningBid, String checkoutUrl) {
        String formatted = NumberFormat.getCurrencyInstance(Locale.US).format(winningBid);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(winnerEmail);
            helper.setSubject("You won: " + auctionTitle);
            helper.setText(buildHtml(auctionTitle, formatted, checkoutUrl), true);
            mailSender.send(message);
            log.info("Sent auction-won email to {} for '{}'", winnerEmail, auctionTitle);
        } catch (MessagingException e) {
            log.error("Failed to send auction-won email to {}: {}", winnerEmail, e.getMessage());
        }
    }

    private String buildHtml(String title, String formattedBid, String checkoutUrl) {
        String checkoutSection = checkoutUrl != null
                ? """
                  <p style="margin:24px 0 0;">
                    <a href="%s" style="background:#1a1a1a;color:#fff;padding:12px 24px;text-decoration:none;font-size:14px;">
                      Complete your purchase
                    </a>
                  </p>
                  """.formatted(checkoutUrl)
                : "<p style='color:#666;margin-top:16px;'>Our team will be in touch with payment details shortly.</p>";

        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family:sans-serif;background:#f5f5f5;padding:32px;">
                  <div style="max-width:520px;margin:0 auto;background:#fff;padding:32px;">
                    <p style="font-size:11px;letter-spacing:3px;text-transform:uppercase;color:#888;">Aether Terra</p>
                    <h1 style="font-size:24px;font-weight:300;margin:16px 0;">You won the auction.</h1>
                    <p style="color:#444;line-height:1.7;">
                      Congratulations — your bid of <strong>%s</strong> won the auction
                      for <strong>%s</strong>.
                    </p>
                    <p style="color:#444;line-height:1.7;">
                      Your shirt will be made to order after payment is confirmed.
                    </p>
                    %s
                    <p style="margin-top:32px;font-size:12px;color:#aaa;">
                      This is an automated message from Aether Terra.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(formattedBid, title, checkoutSection);
    }
}
