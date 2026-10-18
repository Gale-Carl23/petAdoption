
package com.petdonation.service;

import com.petdonation.model.Donation;
import com.petdonation.repository.DonationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DonationService {

    private final DonationRepository donationRepository;

    public DonationService(DonationRepository donationRepository) {
        this.donationRepository = donationRepository;
    }

    @Transactional
    public Donation saveDonation(Donation donation) {
        return donationRepository.save(donation);
    }

    @Transactional(readOnly = true)
    public List<Donation> getDonationsByDonor(Long donorId) {
        return donationRepository
                .findByDonorIdOrderBySubmittedAtDesc(donorId);
    }
}
