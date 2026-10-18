
package com.petdonation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class DonationProofStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final Path storageDirectory;

    public DonationProofStorageService(
            @Value("${app.upload.donation-proof-dir:uploads/donation-proofs}")
            String storageDirectory) {

        this.storageDirectory = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();
    }

    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Proof of donation is required.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "Proof file must not exceed 5 MB.");
        }

        byte[] content = file.getBytes();
        String extension = detectExtension(content);

        if (extension == null) {
            throw new IllegalArgumentException(
                    "Upload a valid JPG, PNG, or PDF file.");
        }

        Files.createDirectories(storageDirectory);

        // Generate our own filename; never trust the uploaded filename.
        String generatedName = UUID.randomUUID() + extension;

        Path destination = storageDirectory
                .resolve(generatedName)
                .normalize();

        if (!destination.getParent().equals(storageDirectory)) {
            throw new IllegalArgumentException("Invalid storage path.");
        }

        Files.write(destination, content);

        // Store this filename in the database, not an absolute file path.
        return generatedName;
    }

    private String detectExtension(byte[] content) {
        // PDF signature: %PDF-
        if (content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-') {
            return ".pdf";
        }

        // PNG signature
        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89
                && content[1] == 'P'
                && content[2] == 'N'
                && content[3] == 'G'
                && (content[4] & 0xFF) == 0x0D
                && (content[5] & 0xFF) == 0x0A
                && (content[6] & 0xFF) == 0x1A
                && (content[7] & 0xFF) == 0x0A) {
            return ".png";
        }

        // JPEG signature
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }

        return null;
    }
}
