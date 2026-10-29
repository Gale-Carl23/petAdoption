package com.petdonation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

@Service
public class DonationProofStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final String ALLOWED_NAME = "^[0-9a-f-]{36}\\.(pdf|png|jpg)$";

    private final Path storageDirectory;
    private final String bucket;
    private final String prefix;
    private final S3Client s3Client;
    private final String blobToken;
    private final String blobStoreId;
    private final HttpClient httpClient;

    public DonationProofStorageService(
            @Value("${app.upload.donation-proof-dir:uploads/donation-proofs}")
            String storageDirectory,
            @Value("${app.upload.s3-bucket:}") String bucket,
            @Value("${app.upload.s3-prefix:donation-proofs}") String prefix,
            @Value("${app.aws.region:${AWS_REGION:ap-southeast-1}}") String region,
            @Value("${app.upload.require-s3:false}") boolean requireS3,
            @Value("${app.upload.storage:local}") String storageProvider,
            @Value("${BLOB_READ_WRITE_TOKEN:}") String readWriteToken,
            @Value("${VERCEL_OIDC_TOKEN:}") String oidcToken,
            @Value("${BLOB_STORE_ID:}") String configuredBlobStoreId) {
        this.storageDirectory = Paths.get(storageDirectory).toAbsolutePath().normalize();
        this.bucket = bucket.trim();
        String provider = storageProvider.trim().toLowerCase(Locale.ROOT);
        this.blobToken = readWriteToken.isBlank() ? oidcToken.trim() : readWriteToken.trim();
        String explicitStoreId = configuredBlobStoreId.trim();
        this.blobStoreId = explicitStoreId.isBlank()
                ? storeIdFromToken(this.blobToken)
                : normalizeStoreId(explicitStoreId);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        if (requireS3 && !provider.equals("s3")) {
            throw new IllegalStateException(
                    "Production donation proof storage must use the configured S3 provider.");
        }
        if (!provider.equals("local") && !provider.equals("s3") && !provider.equals("vercel-blob"))
            throw new IllegalStateException("Unsupported donation proof storage provider.");
        if (provider.equals("vercel-blob") && (this.blobToken.isEmpty() || this.blobStoreId.isEmpty()))
            throw new IllegalStateException("Vercel Blob storage requires BLOB_READ_WRITE_TOKEN, or VERCEL_OIDC_TOKEN with BLOB_STORE_ID, from a connected private Blob store.");
        this.storageProvider = provider;
        this.prefix = normalizePrefix(prefix);
        if (provider.equals("s3") && this.bucket.isEmpty())
            throw new IllegalStateException("S3 storage requires DONATION_PROOF_BUCKET.");
        this.s3Client = !provider.equals("s3")
                ? null
                : S3Client.builder().region(Region.of(region)).build();
    }

    private final String storageProvider;

    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Proof of donation is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Proof file must not exceed 5 MB.");
        }

        byte[] content = file.getBytes();
        String extension = detectExtension(content);
        if (extension == null) {
            throw new IllegalArgumentException("Upload a valid JPG, PNG, or PDF file.");
        }

        String generatedName = UUID.randomUUID() + extension;
        if (storageProvider.equals("s3")) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(generatedName))
                    .contentType(contentType(extension))
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
        } else if (storageProvider.equals("vercel-blob")) {
            HttpRequest request = HttpRequest.newBuilder(blobApiUri(generatedName))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + blobToken)
                    .header("x-api-version", "13")
                    .header("x-vercel-blob-store-id", blobStoreId)
                    .header("x-api-blob-request-id", blobStoreId + ":" + UUID.randomUUID())
                    .header("x-api-blob-request-attempt", "0")
                    .header("x-vercel-blob-access", "private")
                    .header("x-content-type", contentType(extension))
                    .header("x-add-random-suffix", "0")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();
            HttpResponse<byte[]> response = sendBlobRequest(request,
                    "Could not store donation proof in private Vercel Blob storage.");
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Could not store donation proof in private Vercel Blob storage.");
        } else {
            Files.createDirectories(storageDirectory);
            Path destination = storageDirectory.resolve(generatedName).normalize();
            if (!destination.getParent().equals(storageDirectory)) {
                throw new IllegalArgumentException("Invalid storage path.");
            }
            Files.write(destination, content);
        }

        // Keep an opaque generated filename in the database, never a user path or URL.
        return generatedName;
    }

    public Optional<Resource> load(String fileName) throws IOException {
        if (!isValidGeneratedName(fileName)) return Optional.empty();
        if (storageProvider.equals("local")) {
            Path proofFile = storageDirectory.resolve(fileName).normalize();
            if (!proofFile.startsWith(storageDirectory) || !Files.isRegularFile(proofFile)) {
                return Optional.empty();
            }
            return Optional.of(new FileSystemResource(proofFile));
        }

        if (storageProvider.equals("vercel-blob")) {
            HttpRequest request = HttpRequest.newBuilder(blobFileUri(fileName))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + blobToken)
                    .GET().build();
            HttpResponse<byte[]> response = sendBlobRequest(request,
                    "Could not load donation proof from private Vercel Blob storage.");
            if (response.statusCode() == 404) return Optional.empty();
            return Optional.of(new ByteArrayResource(response.body()));
        }

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(fileName))
                    .build();
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(request);
            return Optional.of(new ByteArrayResource(object.asByteArray()));
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) return Optional.empty();
            throw new IOException("Could not load donation proof from private storage.", ex);
        }
    }

    @PreDestroy
    public void close() {
        if (s3Client != null) s3Client.close();
    }

    private String objectKey(String fileName) {
        return prefix.isEmpty() ? fileName : prefix + "/" + fileName;
    }

    private URI blobApiUri(String fileName) {
        String pathname = URLEncoder.encode(objectKey(fileName), java.nio.charset.StandardCharsets.UTF_8);
        return URI.create("https://vercel.com/api/blob/?pathname=" + pathname);
    }

    private URI blobFileUri(String fileName) {
        return URI.create("https://" + blobStoreId + ".private.blob.vercel-storage.com/" + objectKey(fileName));
    }

    private HttpResponse<byte[]> sendBlobRequest(HttpRequest request, String errorMessage) throws IOException {
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300 || response.statusCode() == 404)
                return response;
            throw new IOException(errorMessage + " Storage returned HTTP " + response.statusCode() + ".");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(errorMessage, ex);
        }
    }

    private static String storeIdFromToken(String token) {
        if (token == null || token.isBlank()) return "";
        String[] parts = token.trim().split("_", 4);
        if (parts.length < 4 || !parts[0].equals("vercel") || !parts[1].equals("blob") || !parts[2].equals("rw"))
            return "";
        return parts[3].split("_", 2)[0];
    }

    private static String normalizeStoreId(String storeId) {
        return storeId.startsWith("store_") ? storeId.substring("store_".length()) : storeId;
    }

    private static String normalizePrefix(String value) {
        String prefix = value == null ? "" : value.trim().replace('\\', '/');
        while (prefix.startsWith("/")) prefix = prefix.substring(1);
        while (prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
        if (prefix.contains("..")) throw new IllegalArgumentException("Invalid S3 prefix.");
        return prefix;
    }

    private static boolean isValidGeneratedName(String fileName) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).matches(ALLOWED_NAME)) {
            return false;
        }
        String uuid = fileName.substring(0, 36);
        try {
            return UUID.fromString(uuid).toString().equalsIgnoreCase(uuid);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String contentType(String extension) {
        return switch (extension) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            default -> "image/jpeg";
        };
    }

    private String detectExtension(byte[] content) {
        if (content.length >= 5 && content[0] == '%' && content[1] == 'P'
                && content[2] == 'D' && content[3] == 'F' && content[4] == '-') {
            return ".pdf";
        }
        if (content.length >= 8 && (content[0] & 0xFF) == 0x89
                && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && (content[4] & 0xFF) == 0x0D && (content[5] & 0xFF) == 0x0A
                && (content[6] & 0xFF) == 0x1A && (content[7] & 0xFF) == 0x0A) {
            return ".png";
        }
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        return null;
    }
}
