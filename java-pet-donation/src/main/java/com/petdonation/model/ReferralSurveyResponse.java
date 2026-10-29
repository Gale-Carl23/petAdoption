package com.petdonation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_survey_responses")
public class ReferralSurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "respondent_user_id", nullable = false)
    private User respondent;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", unique = true)
    private AdoptionApplication application;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donation_id", unique = true)
    private Donation donation;

    @Column(name = "referral_source", nullable = false, length = 40)
    private String referralSource;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    public ReferralSurveyResponse() {
    }

    public Long getId() { return id; }
    public User getRespondent() { return respondent; }
    public void setRespondent(User respondent) { this.respondent = respondent; }
    public AdoptionApplication getApplication() { return application; }
    public void setApplication(AdoptionApplication application) { this.application = application; }
    public Donation getDonation() { return donation; }
    public void setDonation(Donation donation) { this.donation = donation; }
    public String getReferralSource() { return referralSource; }
    public void setReferralSource(String referralSource) { this.referralSource = referralSource; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
