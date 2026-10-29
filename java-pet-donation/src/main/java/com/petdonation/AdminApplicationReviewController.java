package com.petdonation;

import com.petdonation.repository.AdoptionApplicationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminApplicationReviewController {

    private final AdoptionApplicationRepository applicationRepository;

    public AdminApplicationReviewController(
            AdoptionApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/admin/applications")
    public String listApplications(Model model) {
        model.addAttribute("applications",
                applicationRepository.findAllWithPetAndApplicantOrderBySubmittedAtDesc());
        return "admin/applications";
    }
}
