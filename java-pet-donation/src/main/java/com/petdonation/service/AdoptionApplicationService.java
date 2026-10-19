package com.petdonation.service;

import com.petdonation.model.AdoptionApplication;
import com.petdonation.repository.AdoptionApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdoptionApplicationService {

    private final AdoptionApplicationRepository applicationRepository;

    public AdoptionApplicationService(
            AdoptionApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public AdoptionApplication submitApplication(
            AdoptionApplication application) {

        if (application == null) {
            throw new IllegalArgumentException(
                    "Application is required.");
        }

        if (application.getApplicant() == null
                || application.getPet() == null) {
            throw new IllegalArgumentException(
                    "Applicant and pet are required.");
        }

        if (application.getReasonForAdoption() == null
                || application.getReasonForAdoption().isBlank()) {
            throw new IllegalArgumentException(
                    "Reason for adoption is required.");
        }

        if (application.getHousingType() == null
                || application.getHousingType().isBlank()) {
            throw new IllegalArgumentException(
                    "Housing type is required.");
        }

        if (application.getPetExperience() == null
                || application.getPetExperience().isBlank()) {
            throw new IllegalArgumentException(
                    "Pet experience is required.");
        }

        Long applicantId = application.getApplicant().getId();
        Long petId = application.getPet().getId();

        boolean duplicate = applicationRepository
                .existsByApplicantIdAndPetIdAndStatusIn(
                        applicantId,
                        petId,
                        List.of("PENDING", "UNDER_REVIEW", "APPROVED")
                );

        if (duplicate) {
            throw new IllegalArgumentException(
                    "You already have an active application for this pet.");
        }

        application.setStatus("PENDING");
        application.setSubmittedAt(LocalDateTime.now());

        return applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public List<AdoptionApplication> getApplicationsByApplicant(
            Long applicantId) {
        return applicationRepository
                .findApplicationsWithPetByApplicantId(applicantId);

    }

    @Transactional(readOnly = true)
    public boolean hasActiveApplication(Long applicantId, Long petId) {
        return applicationRepository.existsByApplicantIdAndPetIdAndStatusIn(
                applicantId,
                petId,
                List.of("PENDING", "UNDER_REVIEW", "APPROVED")
        );
    }
}