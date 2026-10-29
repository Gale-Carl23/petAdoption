package com.petdonation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petdonation.model.Donation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class PayMongoCheckoutService {
    private final String secretKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();

    public PayMongoCheckoutService(
            @Value("${app.paymongo.secret-key:}") String secretKey,
            ObjectMapper objectMapper) {
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() { return !secretKey.isBlank(); }

    public String createQrCheckout(Donation donation, String publicBaseUrl)
            throws IOException, InterruptedException {
        if (!isConfigured()) throw new IllegalStateException("Online QR payments are not configured.");
        URI publicUri = URI.create(publicBaseUrl);
        if (secretKey.startsWith("sk_live_") && !"https".equalsIgnoreCase(publicUri.getScheme())) {
            throw new IllegalStateException("Live PayMongo checkout requires the public HTTPS site URL.");
        }
        long amountCentavos = donation.getAmount().setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2).longValueExact();
        String reference = "DONATION-" + donation.getId();
        Map<String, Object> lineItem = Map.of(
                "name", "Donation to Pawprints of Hope",
                "amount", amountCentavos,
                "currency", "PHP",
                "quantity", 1);
        Map<String, Object> attributes = Map.of(
                "line_items", List.of(lineItem),
                "payment_method_types", List.of("qrph"),
                "success_url", publicBaseUrl + "/donations/payment-result?id=" + donation.getId(),
                "cancel_url", publicBaseUrl + "/donations/my-donations",
                "reference_number", reference,
                "send_email_receipt", false,
                "metadata", Map.of("donation_id", donation.getId().toString()));
        String body = objectMapper.writeValueAsString(Map.of(
                "data", Map.of("attributes", attributes)));
        String basic = Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.paymongo.com/v2/checkout_sessions"))
                .timeout(java.time.Duration.ofSeconds(20))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", donation.getPaymentIdempotencyKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("PayMongo could not create a checkout session (HTTP " + response.statusCode() + ").");
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode data = root.path("data");
        String sessionId = data.path("id").asText();
        String checkoutUrl = data.path("attributes").path("checkout_url").asText();
        URI checkoutUri;
        try { checkoutUri = URI.create(checkoutUrl); }
        catch (IllegalArgumentException exception) { throw new IOException("PayMongo returned an invalid checkout link."); }
        if (sessionId.isBlank() || !"https".equalsIgnoreCase(checkoutUri.getScheme())
                || !"checkout.paymongo.com".equalsIgnoreCase(checkoutUri.getHost())) {
            throw new IOException("PayMongo returned an invalid checkout session.");
        }
        donation.setPaymongoSessionId(sessionId);
        donation.setPaymongoCheckoutUrl(checkoutUrl);
        return checkoutUrl;
    }

    public JsonNode retrieveCheckout(String sessionId) throws IOException, InterruptedException {
        if (!isConfigured() || sessionId == null || !sessionId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("Invalid PayMongo checkout session.");
        }
        String basic = Base64.getEncoder().encodeToString(
                (secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "https://api.paymongo.com/v1/checkout_sessions/" + sessionId))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Authorization", "Basic " + basic)
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("PayMongo could not retrieve the checkout status.");
        }
        return objectMapper.readTree(response.body()).path("data");
    }
}
