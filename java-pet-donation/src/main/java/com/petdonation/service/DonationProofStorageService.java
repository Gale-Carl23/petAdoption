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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class DonationProofStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final String ALLOWED_NAME = "^[0-9a-f-]{36}\\.(pdf|png|jpg)$";

    private final Path storageDirectory;
    private final String bucket;
    private final String prefix;
    private final S3Client s3Client;

    public DonationProofStorageService(
            @Value("${app.upload.donation-proof-dir:uploads/donation-proofs}")
            String storageDirectory,
            @Value("${app.upload.s3-bucket:}") String bucket,
            @Value("${app.upload.s3-prefix:donation-proofs}") String prefix,
            @Value("${app.aws.region:${AWS_REGION:ap-southeast-1}}") String region,
            @Value("${app.upload.require-s3:false}") boolean requireS3) {
        this.storageDirectory = Paths.get(storageDirectory).toAbsolutePath().normalize();
        this.bucket = bucket.trim();
        if (requireS3 && this.bucket.isEmpty()) {
            throw new IllegalStateException(
                    "Production donation proof storage requires a private S3 bucket.");
        }
        this.prefix = normalizePrefix(prefix);
        this.s3Client = this.bucket.isEmpty()
                ? null
                : S3Client.builder().region(Region.of(region)).build();
    }

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
        if (s3Client != null) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(generatedName))
                    .contentType(contentType(extension))
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
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
        if (s3Client == null) {
            Path proofFile = storageDirectory.resolve(fileName).normalize();
            if (!proofFile.startsWith(storageDirectory) || !Files.isRegularFile(proofFile)) {
                return Optional.empty();
            }
            return Optional.of(new FileSystemResource(proofFile));
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
