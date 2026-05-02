package com.aetherterra.commerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Creates Shopify Draft Orders for auction winners using the Admin GraphQL API.
 * The winner receives an invoice email with a payment link (checkoutUrl).
 */
public class ShopifyCommerceOrderProvider implements CommerceOrderProvider {

    private static final Logger log = LoggerFactory.getLogger(ShopifyCommerceOrderProvider.class);

    private final String shopDomain;
    private final String apiVersion;
    private final String accessToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String MUTATION = """
            mutation draftOrderCreate($input: DraftOrderInput!) {
              draftOrderCreate(input: $input) {
                draftOrder {
                  id
                  name
                  invoiceUrl
                }
                userErrors { field message }
              }
            }
            """;

    public ShopifyCommerceOrderProvider(String shopDomain, String apiVersion, String accessToken) {
        this.shopDomain = shopDomain;
        this.apiVersion = apiVersion;
        this.accessToken = accessToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("CommerceOrderProvider: Shopify (shop: {})", shopDomain);
    }

    @Override
    public PostAuctionCheckoutResult createPostAuctionCheckout(PostAuctionCheckoutRequest request) {
        String graphqlUrl = "https://" + shopDomain + "/admin/api/" + apiVersion + "/graphql.json";

        String lineItemTitle = "Aether Terra — " + request.auctionTitle()
                + (request.shirtSize() != null ? " (Size " + request.shirtSize() + ")" : "");

        String variables = buildVariables(request.winnerEmail(), lineItemTitle,
                request.winningBid().toPlainString(), request.auctionId().toString(),
                request.shirtSize());

        String body;
        try {
            body = objectMapper.writeValueAsString(
                    java.util.Map.of("query", MUTATION, "variables", objectMapper.readTree(variables)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Shopify request", e);
        }

        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(graphqlUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Shopify-Access-Token", accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Shopify API returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode errors = root.path("data").path("draftOrderCreate").path("userErrors");
            if (errors.isArray() && !errors.isEmpty()) {
                String errMsg = errors.get(0).path("message").asText("Unknown error");
                throw new RuntimeException("Shopify draft order error: " + errMsg);
            }

            JsonNode order = root.path("data").path("draftOrderCreate").path("draftOrder");
            String shopifyId = order.path("id").asText();
            String orderName = order.path("name").asText();
            String invoiceUrl = order.path("invoiceUrl").asText(null);

            log.info("Shopify Draft Order created: {} ({}) for auction '{}' winner {}",
                    orderName, shopifyId, request.auctionSlug(), request.winnerEmail());

            return new PostAuctionCheckoutResult(providerName(), shopifyId, invoiceUrl);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Shopify API call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "SHOPIFY";
    }

    private String buildVariables(String email, String lineItemTitle, String price,
                                  String auctionId, String shirtSize) {
        String sizeAttr = shirtSize != null
                ? """
                  {"key": "shirt_size", "value": "%s"},
                  """.formatted(shirtSize)
                : "";
        return """
                {
                  "input": {
                    "email": "%s",
                    "lineItems": [{
                      "title": "%s",
                      "originalUnitPrice": "%s",
                      "quantity": 1
                    }],
                    "customAttributes": [
                      %s
                      {"key": "auction_id", "value": "%s"},
                      {"key": "source", "value": "aetherterra_auction"}
                    ]
                  }
                }
                """.formatted(email, lineItemTitle, price, sizeAttr, auctionId);
    }
}
