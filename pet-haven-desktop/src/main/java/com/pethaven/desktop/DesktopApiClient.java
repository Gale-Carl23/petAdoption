package com.pethaven.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Session based API client shared by the JavaFX administration screens. */
public final class DesktopApiClient {

    private static final TypeReference<List<PetRecord>> PET_LIST = new TypeReference<>() { };
    private static final TypeReference<List<ApplicationRecord>> APPLICATION_LIST = new TypeReference<>() { };
    private static final TypeReference<List<DonationRecord>> DONATION_LIST = new TypeReference<>() { };
    private static final TypeReference<List<ReferralSourceRecord>> REFERRAL_LIST = new TypeReference<>() { };

    private final String baseUrl;
    private final CookieManager cookieManager;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String csrfToken;
    private volatile boolean authenticated;
    private volatile String username;

    public DesktopApiClient() {
        this(configuredBaseUrl());
    }

    DesktopApiClient(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void login(String email, String password)
            throws IOException, InterruptedException {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");

        JsonNode csrf = getJson("/api/auth/csrf");
        String token = csrf.path("token").asText();
        if (token.isBlank()) {
            throw new IOException("The server did not provide a login security token.");
        }

        String body = "email=" + encode(email.trim())
                + "&password=" + encode(password)
                + "&_csrf=" + encode(token);
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());

        if (response.uri().getRawQuery() != null
                && response.uri().getRawQuery().contains("error")) {
            clearSession();
            throw new SecurityException("Email or password is incorrect.");
        }

        csrfToken = getJson("/api/auth/csrf").path("token").asText();
        JsonNode summary = getJson("/api/admin/dashboard/summary");
        if (!summary.has("totalPets")) {
            clearSession();
            throw new IOException("The server returned an invalid admin response.");
        }
        username = email.trim();
        authenticated = true;
    }

    public DashboardSummary dashboardSummary() throws IOException, InterruptedException {
        JsonNode node = getJson("/api/admin/dashboard/summary");
        return new DashboardSummary(
                node.path("totalPets").asLong(),
                node.path("totalApplications").asLong(),
                node.path("totalDonations").asLong());
    }

    public List<PetRecord> pets() throws IOException, InterruptedException {
        return getList("/api/admin/pets", PET_LIST);
    }

    public List<ApplicationRecord> applications() throws IOException, InterruptedException {
        return getList("/api/admin/applications", APPLICATION_LIST);
    }

    public List<DonationRecord> donations() throws IOException, InterruptedException {
        return getList("/api/admin/donations", DONATION_LIST);
    }

    public List<ReferralSourceRecord> referralAnalytics() throws IOException, InterruptedException {
        return getList("/api/admin/dashboard/referrals", REFERRAL_LIST);
    }

    public ApplicationRecord reviewApplication(long id, String status)
            throws IOException, InterruptedException {
        return sendJson("PATCH", "/api/admin/applications/" + id + "/status",
                new ReviewDecision(status), ApplicationRecord.class);
    }

    public DonationRecord reviewDonation(long id, String status)
            throws IOException, InterruptedException {
        return sendJson("PATCH", "/api/admin/donations/" + id + "/status",
                new ReviewDecision(status), DonationRecord.class);
    }

    public PetRecord createPet(PetPayload pet) throws IOException, InterruptedException {
        return sendJson("POST", "/api/admin/pets", pet, PetRecord.class);
    }

    public PetRecord updatePetAvailability(long id, String status) throws IOException, InterruptedException {
        return sendJson("PATCH", "/api/admin/pets/" + id + "/availability",
                new PetAvailability(status), PetRecord.class);
    }

    public PetRecord updatePet(long id, PetPayload pet) throws IOException, InterruptedException {
        return sendJson("PUT", "/api/admin/pets/" + id, pet, PetRecord.class);
    }

    public byte[] donationProof(long id) throws IOException, InterruptedException {
        HttpRequest request = authorizedRequest("/api/admin/donations/" + id + "/proof")
                .GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        requireSuccess(response.statusCode(), "Could not open donation proof");
        return response.body();
    }

    public void logout() throws IOException, InterruptedException {
        if (csrfToken == null || !authenticated) {
            clearSession();
            return;
        }
        String body = "_csrf=" + encode(csrfToken);
        send(HttpRequest.newBuilder(uri("/logout"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-CSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
        clearSession();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String username() {
        return username == null ? "Administrator" : username;
    }

    private <T> List<T> getList(String path, TypeReference<List<T>> type)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send(authorizedRequest(path).GET().build());
        requireJsonSuccess(response);
        return objectMapper.readValue(response.body(), type);
    }

    private <T> T sendJson(String method, String path, Object body, Class<T> responseType)
            throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(body);
        HttpRequest request = authorizedRequest(path)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = send(request);
        requireJsonSuccess(response);
        return objectMapper.readValue(response.body(), responseType);
    }

    private JsonNode getJson(String path) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET().build());
        requireJsonSuccess(response);
        return objectMapper.readTree(response.body());
    }

    private HttpRequest.Builder authorizedRequest(String path) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/json");
        String token = csrfToken;
        if (token != null && !token.isBlank()) {
            request.header("X-CSRF-TOKEN", token);
        }
        return request;
    }

    private HttpResponse<String> send(HttpRequest request)
            throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void requireJsonSuccess(HttpResponse<String> response) throws IOException {
        requireSuccess(response.statusCode(), "The server request failed");
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (!contentType.toLowerCase().contains("application/json")) {
            throw new IOException("The server did not return JSON. Please sign in again.");
        }
    }

    private void requireSuccess(int status, String message) throws IOException {
        if (status == 401 || status == 403) {
            authenticated = false;
            throw new SecurityException("Your admin session expired or does not have permission. Please sign in again.");
        }
        if (status < 200 || status >= 300) {
            throw new IOException(message + " (HTTP " + status + ").");
        }
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private void clearSession() {
        csrfToken = null;
        authenticated = false;
        username = null;
        cookieManager.getCookieStore().removeAll();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String configuredBaseUrl() {
        String environment = System.getenv("PET_HAVEN_API_BASE_URL");
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        return System.getProperty("petHaven.api.baseUrl", "http://localhost:8080");
    }

    private static String normalizeBaseUrl(String value) {
        URI uri = URI.create(Objects.requireNonNull(value, "baseUrl").trim());
        if (uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("The API base URL is invalid.");
        }
        String host = uri.getHost().toLowerCase();
        boolean loopback = host.equals("localhost") || host.equals("127.0.0.1")
                || host.equals("::1");
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopback) {
            throw new IllegalArgumentException("The API must use HTTPS outside the local machine.");
        }
        return value.trim().replaceAll("/+$", "");
    }

    public record DashboardSummary(long totalPets, long totalApplications, long totalDonations) { }

    public record PetRecord(Long id, String name, String breed, Integer age,
                            String description, String imageUrl, String status) { }

    public record PetPayload(String name, String breed, Integer age,
                             String description, String imageUrl) { }

    public record ApplicationRecord(Long id, String applicantName, String applicantEmail,
                                    Long petId, String petName, String reason,
                                    String housingType, String petExperience,
                                    String status, String submittedAt,
                                    String reviewedBy, String reviewedAt) { }

    public record DonationRecord(Long id, String donorName, String donorEmail,
                                 BigDecimal amount, String dateSent, String status,
                                 String message, String submittedAt, String proofPath,
                                 String reviewedBy, String reviewedAt,
                                 String paymentMethod, String paymentReference) { }

    public record ReferralSourceRecord(String source, String label, long count) { }

    private record ReviewDecision(String status) { }
    private record PetAvailability(String status) { }
}
