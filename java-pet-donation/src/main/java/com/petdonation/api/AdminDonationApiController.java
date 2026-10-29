package com.petdonation.api;

import com.petdonation.model.Donation;
import com.petdonation.repository.DonationRepository;
import com.petdonation.service.AdminReviewService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/donations")
public class AdminDonationApiController {

    private final DonationRepository donationRepository;
    private final AdminReviewService reviewService;

    public AdminDonationApiController(DonationRepository donationRepository,
                                      AdminReviewService reviewService) {
        this.donationRepository = donationRepository;
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<DonationResponse> listDonations() {
        return donationRepository.findAllWithDonorOrderBySubmittedAtDesc()
                .stream().map(DonationResponse::from).toList();
    }

    @PatchMapping("/{id}/status")
    public DonationResponse updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody ReviewDecision decision) {
        return DonationResponse.from(reviewService.reviewDonation(id, decision.status()));
    }

    public record ReviewDecision(@NotBlank String status) { }

    public record DonationResponse(
            Long id,
            String donorName,
            String donorEmail,
            BigDecimal amount,
            LocalDate dateSent,
            String status,
            String message,
            LocalDateTime submittedAt,
            String proofPath,
            String reviewedBy,
            LocalDateTime reviewedAt,
            String paymentMethod,
            String paymentReference) {

        static DonationResponse from(Donation donation) {
            String donorName = donation.getFullName() == null
                    || donation.getFullName().isBlank()
                    ? donation.getDonor().getFullName()
                    : donation.getFullName();
            return new DonationResponse(
                    donation.getId(),
                    donorName,
                    donation.getDonor().getEmail(),
                    donation.getAmount(),
                    donation.getDateSent(),
                    donation.getStatus(),
                    donation.getMessage(),
                    donation.getSubmittedAt(),
                    donation.getProofFilePath() == null ? null
                            : "/api/admin/donations/" + donation.getId() + "/proof",
                    donation.getReviewedBy(),
                    donation.getReviewedAt(),
                    donation.getPaymentMethod(),
                    donation.getPaymentReference());
        }
    }
}
