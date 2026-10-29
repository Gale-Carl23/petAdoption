
package com.petdonation.repository;

import com.petdonation.model.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import jakarta.persistence.LockModeType;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByDonorIdOrderBySubmittedAtDesc(Long donorId);

    @Query("""
    SELECT d
    FROM Donation d
    JOIN FETCH d.donor
    ORDER BY d.submittedAt DESC
    """)
    List<Donation> findAllWithDonorOrderBySubmittedAtDesc();

    @Query("""
    SELECT d
    FROM Donation d
    JOIN FETCH d.donor
    WHERE d.id = :id
    """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Donation> findByIdWithDonor(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Donation> findByPaymongoSessionId(String paymongoSessionId);

}
