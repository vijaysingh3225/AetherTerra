package com.aetherterra.webhooks;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedShopifyWebhookRepository extends JpaRepository<ProcessedShopifyWebhook, String> {}
