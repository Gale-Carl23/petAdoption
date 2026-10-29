package com.petdonation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petdonation.model.Donation;
import com.petdonation.model.ProcessedPaymentWebhook;
import com.petdonation.repository.DonationRepository;
import com.petdonation.repository.ProcessedPaymentWebhookRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Service
public class PayMongoWebhookService {
    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final DonationRepository donationRepository;
    private final ProcessedPaymentWebhookRepository eventRepository;
    private final EntityManager entityManager;
    private final boolean liveMode;

    public PayMongoWebhookService(
            @Value("${app.paymongo.webhook-secret:}") String webhookSecret,
            ObjectMapper objectMapper,
            DonationRepository donationRepository,
            ProcessedPaymentWebhookRepository eventRepository,
            EntityManager entityManager,
            @Value("${app.paymongo.live-mode:false}") boolean liveMode) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.objectMapper = objectMapper;
        this.donationRepository = donationRepository;
        this.eventRepository = eventRepository;
        this.entityManager = entityManager;
        this.liveMode = liveMode;
    }

    @Transactional
    public void accept(byte[] rawBody, String signatureHeader) {
        if (!verify(rawBody, signatureHeader)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment notification signature.");
        }
        final JsonNode root;
        try { root = objectMapper.readTree(rawBody); }
        catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment notification.");
        }
        JsonNode envelope = root.path("data");
        JsonNode attributes = envelope.path("attributes");
        if (!attributes.has("livemode") || attributes.path("livemode").asBoolean() != liveMode) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Payment notification mode mismatch.");
        }
        String eventId = envelope.path("id").asText();
        String eventType = attributes.path("type").asText(envelope.path("type").asText());
        if (eventId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing event ID.");
        if (eventRepository.existsById(eventId)) return;
        if (!"checkout_session.payment.paid".equals(eventType)) {
            return;
        }

        JsonNode session = attributes.path("data");
        if (session.isMissingNode()) session = envelope.path("data");
        confirmSession(session, null);
        if (eventRepository.existsById(eventId)) return;
        eventRepository.save(new ProcessedPaymentWebhook(eventId, LocalDateTime.now(ZoneOffset.UTC)));
    }

    @Transactional
    public void reconcileCheckout(long donationId, JsonNode session) {
        confirmSession(session, donationId);
    }

    private Donation confirmSession(JsonNode session, Long expectedDonationId) {
        String sessionId = session.path("id").asText();
        if (sessionId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing checkout session ID.");
        Donation donation = donationRepository.findByPaymongoSessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation checkout not found."));
        entityManager.refresh(donation, LockModeType.PESSIMISTIC_WRITE);
        if (expectedDonationId != null && !expectedDonationId.equals(donation.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout does not match the donation.");
        }
        JsonNode sessionAttributes = session.path("attributes");
        if (!sessionAttributes.has("livemode") || sessionAttributes.path("livemode").asBoolean() != liveMode) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Checkout mode mismatch.");
        }
        if (!("DONATION-" + donation.getId()).equals(sessionAttributes.path("reference_number").asText())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Donation reference does not match checkout.");
        }
        JsonNode paid = null;
        JsonNode payments = sessionAttributes.path("payments");
        for (JsonNode payment : payments) {
            JsonNode paymentAttributes = payment.path("attributes");
            if ("paid".equalsIgnoreCase(paymentAttributes.path("status").asText())) {
                paid = payment;
                break;
            }
        }
        JsonNode paymentAttributes = paid == null ? objectMapper.createObjectNode() : paid.path("attributes");
        if (paid == null
                || !"PHP".equalsIgnoreCase(paymentAttributes.path("currency").asText())
                || paymentAttributes.path("amount").asLong(-1) != donation.getAmount().movePointRight(2).longValueExact()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid amount or currency does not match donation.");
        }
        if (!"qrph".equalsIgnoreCase(paymentAttributes.path("source").path("type").asText())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment method does not match this QR Ph donation.");
        }
        if (!"VERIFIED".equals(donation.getStatus())) {
            donation.setStatus("VERIFIED");
            donation.setReviewedBy("PayMongo");
            donation.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
            String paymentId = paid.path("id").asText();
            if (!paymentId.isBlank()) donation.setPaymentReference(paymentId);
        }
        return donation;
    }

    private boolean verify(byte[] rawBody, String header) {
        if (webhookSecret.isBlank() || rawBody == null || header == null) return false;
        Map<String, String> parts = new HashMap<>();
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2) parts.put(pair[0].trim(), pair[1].trim());
        }
        String timestamp = parts.get("t");
        if (timestamp == null) return false;
        try {
            long seconds = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - seconds) > 300) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            byte[] expected = mac.doFinal(rawBody);
            return matches(expected, parts.get(liveMode ? "li" : "te"));
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean matches(byte[] expected, String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        try { return MessageDigest.isEqual(expected, java.util.HexFormat.of().parseHex(candidate)); }
        catch (IllegalArgumentException exception) { return false; }
    }
}
