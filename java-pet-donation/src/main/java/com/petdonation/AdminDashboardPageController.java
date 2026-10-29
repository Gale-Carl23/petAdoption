package com.petdonation;

import com.petdonation.repository.AdoptionApplicationRepository;
import com.petdonation.repository.DonationRepository;
import com.petdonation.repository.PetRepository;
import com.petdonation.service.ReferralSurveyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminDashboardPageController {

    private final PetRepository petRepository;
    private final AdoptionApplicationRepository applicationRepository;
    private final DonationRepository donationRepository;
    private final ReferralSurveyService referralSurveyService;

    public AdminDashboardPageController(
            PetRepository petRepository,
            AdoptionApplicationRepository applicationRepository,
            DonationRepository donationRepository,
            ReferralSurveyService referralSurveyService) {
        this.petRepository = petRepository;
        this.applicationRepository = applicationRepository;
        this.donationRepository = donationRepository;
        this.referralSurveyService = referralSurveyService;
    }

    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("petCount", petRepository.count());
        model.addAttribute("applicationCount", applicationRepository.count());
        model.addAttribute("donationCount", donationRepository.count());
        var referralSources = referralSurveyService.analytics();
        model.addAttribute("referralSources", referralSources);
        model.addAttribute("referralResponseCount", referralSources.stream()
                .mapToLong(ReferralSurveyService.ReferralSourceCount::count).sum());
        return "admin/dashboard";
    }
}
