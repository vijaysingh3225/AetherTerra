package com.aetherterra.webhooks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_shopify_webhooks")
public class ProcessedShopifyWebhook {

    @Id
    @Column(name = "webhook_id", length = 255)
    private String webhookId;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt = Instant.now();

    public ProcessedShopifyWebhook() {}

    public ProcessedShopifyWebhook(String webhookId, String topic) {
        this.webhookId = webhookId;
        this.topic = topic;
    }

    public String getWebhookId() { return webhookId; }
    public String getTopic() { return topic; }
    public Instant getProcessedAt() { return processedAt; }
}
