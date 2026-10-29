package com.petdonation.api;

import com.petdonation.model.AdoptionApplication;
import com.petdonation.repository.AdoptionApplicationRepository;
import com.petdonation.service.AdminReviewService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/applications")
public class AdminApplicationApiController {

    private final AdoptionApplicationRepository applicationRepository;
    private final AdminReviewService reviewService;

    public AdminApplicationApiController(
            AdoptionApplicationRepository applicationRepository,
            AdminReviewService reviewService) {
        this.applicationRepository = applicationRepository;
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ApplicationResponse> listApplications() {
        return applicationRepository.findAllWithPetAndApplicantOrderBySubmittedAtDesc()
                .stream().map(ApplicationResponse::from).toList();
    }

    @PatchMapping("/{id}/status")
    public ApplicationResponse updateStatus(@PathVariable Long id,
                                            @RequestBody ReviewDecision decision) {
        return ApplicationResponse.from(
                reviewService.reviewApplication(id, decision.status()));
    }

    public record ReviewDecision(String status) { }

    public record ApplicationResponse(
            Long id,
            String applicantName,
            String applicantEmail,
            Long petId,
            String petName,
            String reason,
            String housingType,
            String petExperience,
            String status,
            LocalDateTime submittedAt) {

        static ApplicationResponse from(AdoptionApplication application) {
            return new ApplicationResponse(
                    application.getId(),
                    application.getApplicant().getFullName(),
                    application.getApplicant().getEmail(),
                    application.getPet().getId(),
                    application.getPet().getName(),
                    application.getReasonForAdoption(),
                    application.getHousingType(),
                    application.getPetExperience(),
                    application.getStatus(),
                    application.getSubmittedAt());
        }
    }
}
