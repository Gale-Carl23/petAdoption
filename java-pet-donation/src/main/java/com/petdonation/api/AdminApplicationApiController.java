package com.petdonation.api;

import com.petdonation.model.AdoptionApplication;
import com.petdonation.repository.AdoptionApplicationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/applications")
public class AdminApplicationApiController {

    private final AdoptionApplicationRepository applicationRepository;

    public AdminApplicationApiController(
            AdoptionApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @GetMapping
    public List<ApplicationResponse> listApplications() {
        return applicationRepository.findAllWithPetAndApplicantOrderBySubmittedAtDesc()
                .stream().map(ApplicationResponse::from).toList();
    }

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
