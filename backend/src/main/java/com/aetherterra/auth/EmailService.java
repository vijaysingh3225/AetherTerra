package com.aetherterra.auth;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String baseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${aetherterra.mail.from}") String fromAddress,
            @Value("${aetherterra.app.base-url}") String baseUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.baseUrl = baseUrl;
    }

    public void sendVerificationEmail(String toEmail, String token) throws Exception {
        String verifyLink = baseUrl + "/verify-email?token=" + token;

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(toEmail);
        helper.setSubject("Verify your Aether Terra account");
        helper.setText(buildVerificationHtml(verifyLink), true);

        mailSender.send(message);
    }

    private String buildVerificationHtml(String verifyLink) {
        return """
                <div style="font-family: sans-serif; max-width: 480px; margin: 0 auto; color: #171717;">
                  <h2 style="font-size: 20px; font-weight: 600; margin-bottom: 8px;">Verify your email</h2>
                  <p style="color: #737373; margin-bottom: 24px;">
                    Thanks for signing up to Aether Terra. Click the button below to verify your email address.
                    This link expires in 24 hours.
                  </p>
                  <a href="%s"
                     style="display: inline-block; background: #171717; color: #fff;
                            text-decoration: none; padding: 10px 20px; border-radius: 6px; font-size: 14px;">
                    Verify email
                  </a>
                  <p style="margin-top: 24px; font-size: 12px; color: #a3a3a3;">
                    If you didn't create an account, you can safely ignore this email.
                  </p>
                </div>
                """.formatted(verifyLink);
    }
}
