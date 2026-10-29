package com.petdonation;

import com.petdonation.repository.AdoptionApplicationRepository;
import com.petdonation.service.AdminReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminApplicationReviewController {

    private final AdoptionApplicationRepository applicationRepository;
    private final AdminReviewService reviewService;

    public AdminApplicationReviewController(
            AdoptionApplicationRepository applicationRepository,
            AdminReviewService reviewService) {
        this.applicationRepository = applicationRepository;
        this.reviewService = reviewService;
    }

    @GetMapping("/admin/applications")
    public String listApplications(Model model) {
        model.addAttribute("applications",
                applicationRepository.findAllWithPetAndApplicantOrderBySubmittedAtDesc());
        return "admin/applications";
    }

    @PostMapping("/admin/applications/{id}/decision")
    public String decideApplication(@PathVariable Long id,
                                    @RequestParam String status,
                                    RedirectAttributes redirectAttributes) {
        reviewService.reviewApplication(id, status);
        redirectAttributes.addFlashAttribute("reviewMessage",
                "Application decision recorded: " + status.toLowerCase() + ".");
        return "redirect:/admin/applications";
    }
}
