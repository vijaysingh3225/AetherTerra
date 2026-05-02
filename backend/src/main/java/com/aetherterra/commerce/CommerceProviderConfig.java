package com.aetherterra.commerce;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommerceProviderConfig {

    @Value("${aetherterra.shopify.mock-mode:true}")
    private boolean mockMode;

    @Value("${aetherterra.shopify.shop-domain:}")
    private String shopDomain;

    @Value("${aetherterra.shopify.api-version:2025-01}")
    private String apiVersion;

    @Value("${aetherterra.shopify.access-token:}")
    private String accessToken;

    @Bean
    public CommerceOrderProvider commerceOrderProvider() {
        boolean credentialsMissing = shopDomain == null || shopDomain.isBlank()
                || accessToken == null || accessToken.isBlank();

        if (mockMode || credentialsMissing) {
            return new MockCommerceOrderProvider();
        }
        return new ShopifyCommerceOrderProvider(shopDomain, apiVersion, accessToken);
    }
}
