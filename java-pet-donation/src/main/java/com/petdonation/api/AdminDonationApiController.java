package com.petdonation.api;

import com.petdonation.model.Donation;
import com.petdonation.repository.DonationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/donations")
public class AdminDonationApiController {

    private final DonationRepository donationRepository;

    public AdminDonationApiController(DonationRepository donationRepository) {
        this.donationRepository = donationRepository;
    }

    @GetMapping
    public List<DonationResponse> listDonations() {
        return donationRepository.findAllWithDonorOrderBySubmittedAtDesc()
                .stream().map(DonationResponse::from).toList();
    }

    public record DonationResponse(
            Long id,
            String donorName,
            String donorEmail,
            BigDecimal amount,
            LocalDate dateSent,
            String status,
            String message,
            LocalDateTime submittedAt,
            String proofPath) {

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
                    "/api/admin/donations/" + donation.getId() + "/proof");
        }
    }
}
