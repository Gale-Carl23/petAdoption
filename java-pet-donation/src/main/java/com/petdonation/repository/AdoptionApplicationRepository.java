package com.petdonation.repository;

import com.petdonation.model.AdoptionApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import jakarta.persistence.LockModeType;

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

    @Query("""
    SELECT a
    FROM AdoptionApplication a
    JOIN FETCH a.pet
    WHERE a.applicant.id = :applicantId
    ORDER BY a.submittedAt DESC
    """)
    List<AdoptionApplication> findApplicationsWithPetByApplicantId(
            @Param("applicantId") Long applicantId
    );

    @Query("""
    SELECT a
    FROM AdoptionApplication a
    JOIN FETCH a.pet
    JOIN FETCH a.applicant
    ORDER BY a.submittedAt DESC
    """)
    List<AdoptionApplication> findAllWithPetAndApplicantOrderBySubmittedAtDesc();

    @Query("""
    SELECT a
    FROM AdoptionApplication a
    JOIN FETCH a.pet
    JOIN FETCH a.applicant
    WHERE a.id = :id
    """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<AdoptionApplication> findByIdWithPetAndApplicant(
            @Param("id") Long id);
}
