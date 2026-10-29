package com.petdonation;

import com.petdonation.model.Donation;
import com.petdonation.repository.DonationRepository;
import com.petdonation.service.AdminReviewService;
import com.petdonation.service.DonationProofStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

@Controller
public class AdminDonationReviewController {

    private final DonationRepository donationRepository;
    private final AdminReviewService reviewService;
    private final DonationProofStorageService proofStorageService;

    public AdminDonationReviewController(
            DonationRepository donationRepository,
            AdminReviewService reviewService,
            DonationProofStorageService proofStorageService) {
        this.donationRepository = donationRepository;
        this.reviewService = reviewService;
        this.proofStorageService = proofStorageService;
    }

    @GetMapping("/admin/donations")
    public String listDonations(Model model) {
        model.addAttribute("donations",
                donationRepository.findAllWithDonorOrderBySubmittedAtDesc());
        return "admin/donations";
    }

    @PostMapping("/admin/donations/{id}/decision")
    public String decideDonation(@PathVariable Long id,
                                 @RequestParam String status,
                                 RedirectAttributes redirectAttributes) {
        reviewService.reviewDonation(id, status);
        redirectAttributes.addFlashAttribute("reviewMessage",
                "Donation decision recorded: " + status.toLowerCase() + ".");
        return "redirect:/admin/donations";
    }

    @GetMapping({
            "/admin/donations/{id}/proof",
            "/api/admin/donations/{id}/proof"
    })
    public ResponseEntity<Resource> viewProof(@PathVariable Long id) throws IOException {
        Donation donation = donationRepository.findById(id).orElse(null);
        if (donation == null || donation.getProofFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        String fileName = donation.getProofFilePath();
        Optional<Resource> proof = proofStorageService.load(fileName);
        if (proof.isEmpty()) return ResponseEntity.notFound().build();
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
                .body(proof.get());
    }
}
