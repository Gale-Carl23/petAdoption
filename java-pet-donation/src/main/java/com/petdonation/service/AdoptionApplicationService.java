package com.petdonation.service;

import com.petdonation.model.AdoptionApplication;
import com.petdonation.repository.AdoptionApplicationRepository;
import com.petdonation.repository.PetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdoptionApplicationService {

    private final AdoptionApplicationRepository applicationRepository;
    private final PetRepository petRepository;

    public AdoptionApplicationService(
            AdoptionApplicationRepository applicationRepository,
            PetRepository petRepository) {
        this.applicationRepository = applicationRepository;
        this.petRepository = petRepository;
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
        // Serialize new applications with each other and with admin approvals for this pet.
        var pet = petRepository.findByIdForUpdate(petId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "This pet profile is no longer available."));
        if (!"AVAILABLE".equals(pet.getStatus())) {
            throw new IllegalArgumentException(
                    "This pet is no longer available for adoption.");
        }
        application.setPet(pet);

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
