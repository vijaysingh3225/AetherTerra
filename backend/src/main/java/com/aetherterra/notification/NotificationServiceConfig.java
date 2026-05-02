package com.aetherterra.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class NotificationServiceConfig {

    @Value("${aetherterra.mail.from:}")
    private String fromAddress;

    @Bean
    public NotificationService notificationService(JavaMailSender mailSender) {
        if (fromAddress != null && !fromAddress.isBlank()) {
            return new EmailNotificationService(mailSender, fromAddress);
        }
        return new LoggingNotificationService();
    }
}
