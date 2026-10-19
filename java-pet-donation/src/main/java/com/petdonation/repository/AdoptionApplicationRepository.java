package com.petdonation.repository;

import com.petdonation.model.AdoptionApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdoptionApplicationRepository
        extends JpaRepository<AdoptionApplication, Long> {

    List<AdoptionApplication>
            findByApplicantIdOrderBySubmittedAtDesc(Long applicantId);

    List<AdoptionApplication>
            findByPetIdOrderBySubmittedAtDesc(Long petId);

    boolean existsByApplicantIdAndPetIdAndStatusIn(
            Long applicantId,
            Long petId,
            List<String> statuses
    );
}