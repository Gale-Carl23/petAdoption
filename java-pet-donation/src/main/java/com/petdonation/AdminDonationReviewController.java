package com.petdonation;

import com.petdonation.model.Donation;
import com.petdonation.repository.DonationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@Controller
public class AdminDonationReviewController {

    private final DonationRepository donationRepository;
    private final Path proofDirectory;

    public AdminDonationReviewController(
            DonationRepository donationRepository,
            @Value("${app.upload.donation-proof-dir:uploads/donation-proofs}")
            String proofDirectory) {
        this.donationRepository = donationRepository;
        this.proofDirectory = Paths.get(proofDirectory).toAbsolutePath().normalize();
    }

    @GetMapping("/admin/donations")
    public String listDonations(Model model) {
        model.addAttribute("donations",
                donationRepository.findAllWithDonorOrderBySubmittedAtDesc());
        return "admin/donations";
    }

    @GetMapping({
            "/admin/donations/{id}/proof",
            "/api/admin/donations/{id}/proof"
    })
    public ResponseEntity<Resource> viewProof(@PathVariable Long id) {
        Donation donation = donationRepository.findById(id).orElse(null);
        if (donation == null || donation.getProofFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        final Path submittedPath;
        try {
            submittedPath = Paths.get(donation.getProofFilePath());
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }

        if (submittedPath.isAbsolute() || submittedPath.getNameCount() != 1) {
            return ResponseEntity.notFound().build();
        }

        Path proofFile = proofDirectory.resolve(submittedPath).normalize();
        if (!proofFile.startsWith(proofDirectory)
                || !Files.isRegularFile(proofFile)) {
            return ResponseEntity.notFound().build();
        }

        String fileName = submittedPath.getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        MediaType mediaType = switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fileName).build().toString())
                .body(new FileSystemResource(proofFile));
    }
}
