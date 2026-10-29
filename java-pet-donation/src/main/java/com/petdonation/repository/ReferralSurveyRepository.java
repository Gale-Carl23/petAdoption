package com.petdonation.repository;

import com.petdonation.model.ReferralSurveyResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReferralSurveyRepository extends JpaRepository<ReferralSurveyResponse, Long> {

    Optional<ReferralSurveyResponse> findByApplicationId(Long applicationId);

    Optional<ReferralSurveyResponse> findByDonationId(Long donationId);

    @Query("""
            SELECT response.referralSource, COUNT(response)
            FROM ReferralSurveyResponse response
            GROUP BY response.referralSource
            """)
    List<Object[]> countResponsesByReferralSource();
}
