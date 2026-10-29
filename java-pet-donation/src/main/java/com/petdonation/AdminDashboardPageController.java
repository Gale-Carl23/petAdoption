package com.petdonation;

import com.petdonation.repository.AdoptionApplicationRepository;
import com.petdonation.repository.DonationRepository;
import com.petdonation.repository.PetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminDashboardPageController {

    private final PetRepository petRepository;
    private final AdoptionApplicationRepository applicationRepository;
    private final DonationRepository donationRepository;

    public AdminDashboardPageController(
            PetRepository petRepository,
            AdoptionApplicationRepository applicationRepository,
            DonationRepository donationRepository) {
        this.petRepository = petRepository;
        this.applicationRepository = applicationRepository;
        this.donationRepository = donationRepository;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("petCount", petRepository.count());
        model.addAttribute("applicationCount", applicationRepository.count());
        model.addAttribute("donationCount", donationRepository.count());
        return "admin/dashboard";
    }
}
