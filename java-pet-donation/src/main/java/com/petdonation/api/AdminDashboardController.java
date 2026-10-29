
package com.petdonation.api;

import com.petdonation.repository.PetRepository;
import com.petdonation.repository.AdoptionApplicationRepository;
import com.petdonation.repository.DonationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final PetRepository petRepository;
    private final AdoptionApplicationRepository applicationRepository;
    private final DonationRepository donationRepository;

    public AdminDashboardController(
            PetRepository petRepository,
            AdoptionApplicationRepository applicationRepository,
            DonationRepository donationRepository) {
        this.petRepository = petRepository;
        this.applicationRepository = applicationRepository;
        this.donationRepository = donationRepository;
    }

    @GetMapping("/summary")
    public Map<String, Long> getSummary() {
        long totalPets = petRepository.count();

        return Map.of(
                "totalPets", totalPets,
                "totalApplications", applicationRepository.count(),
                "totalDonations", donationRepository.count()
        );
    }
}
