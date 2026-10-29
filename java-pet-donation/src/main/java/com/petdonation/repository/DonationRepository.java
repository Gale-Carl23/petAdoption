
package com.petdonation.repository;

import com.petdonation.model.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByDonorIdOrderBySubmittedAtDesc(Long donorId);

    @Query("""
    SELECT d
    FROM Donation d
    JOIN FETCH d.donor
    ORDER BY d.submittedAt DESC
    """)
    List<Donation> findAllWithDonorOrderBySubmittedAtDesc();

}
