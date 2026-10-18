
package com.petdonation.repository;

import com.petdonation.model.Donation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByDonorIdOrderBySubmittedAtDesc(Long donorId);

}
