package com.petdonation.service;

import com.petdonation.model.AdoptionApplication;
import com.petdonation.model.Donation;
import com.petdonation.repository.AdoptionApplicationRepository;
import com.petdonation.repository.DonationRepository;
import com.petdonation.repository.PetRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AdminReviewService {

    private final AdoptionApplicationRepository applicationRepository;
    private final DonationRepository donationRepository;
    private final PetRepository petRepository;
    private final EntityManager entityManager;

    public AdminReviewService(AdoptionApplicationRepository applicationRepository,
                              DonationRepository donationRepository,
                              PetRepository petRepository,
                              EntityManager entityManager) {
        this.applicationRepository = applicationRepository;
        this.donationRepository = donationRepository;
        this.petRepository = petRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public AdoptionApplication reviewApplication(long id, String requestedStatus) {
        AdoptionApplication application = applicationRepository.findByIdWithPetAndApplicant(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Adoption application was not found."));
        String nextStatus = normalize(requestedStatus, "APPROVED", "REJECTED");
        if (nextStatus.equals(application.getStatus())) return application;
        if (!"PENDING".equals(application.getStatus())
                && !"UNDER_REVIEW".equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This application has already been decided.");
        }

        if ("APPROVED".equals(nextStatus)) {
            var pet = petRepository.findByIdForUpdate(application.getPet().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "The pet profile is no longer available."));
            entityManager.refresh(pet, LockModeType.PESSIMISTIC_WRITE);
            if (!"AVAILABLE".equals(pet.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This pet is no longer available for adoption.");
            }
            pet.setStatus("ADOPTED");
            petRepository.save(pet);
            application.setPet(pet);
        }
        application.setStatus(nextStatus);
        return applicationRepository.save(application);
    }

    @Transactional
    public Donation reviewDonation(long id, String requestedStatus) {
        Donation donation = donationRepository.findByIdWithDonor(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Donation submission was not found."));
        String nextStatus = normalize(requestedStatus, "VERIFIED", "REJECTED");
        if (nextStatus.equals(donation.getStatus())) return donation;
        if (!"PENDING_VERIFICATION".equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This donation has already been decided.");
        }
        donation.setStatus(nextStatus);
        return donationRepository.save(donation);
    }

    private static String normalize(String value, String first, String second) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!first.equals(status) && !second.equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The requested review decision is not supported.");
        }
        return status;
    }
}
