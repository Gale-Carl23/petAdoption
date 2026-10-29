package com.petdonation.service;

import com.petdonation.model.ReferralSurveyResponse;
import com.petdonation.repository.AdoptionApplicationRepository;
import com.petdonation.repository.DonationRepository;
import com.petdonation.repository.ReferralSurveyRepository;
import com.petdonation.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReferralSurveyService {

    private static final Map<String, String> SOURCES = Map.of(
            "FACEBOOK", "Facebook",
            "INSTAGRAM_OR_TIKTOK", "Instagram or TikTok",
            "FRIENDS_OR_FAMILY", "Friends or family",
            "SEARCH_ENGINE", "Search engine",
            "COMMUNITY_EVENT", "Community event",
            "VET_OR_SHELTER", "Veterinarian or another shelter",
            "OTHER", "Other");

    private final AdoptionApplicationRepository applicationRepository;
    private final DonationRepository donationRepository;
    private final ReferralSurveyRepository surveyRepository;
    private final UserRepository userRepository;

    public ReferralSurveyService(AdoptionApplicationRepository applicationRepository,
                                 DonationRepository donationRepository,
                                 ReferralSurveyRepository surveyRepository,
                                 UserRepository userRepository) {
        this.applicationRepository = applicationRepository;
        this.donationRepository = donationRepository;
        this.surveyRepository = surveyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void submitForApplication(long applicationId, String userEmail, String source) {
        var application = applicationRepository.findByIdWithPetAndApplicant(applicationId)
                .orElseThrow(() -> notFound("Application was not found."));
        requireOwner(application.getApplicant().getEmail(), userEmail);
        var respondent = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> notFound("Account was not found."));
        ReferralSurveyResponse response = surveyRepository.findByApplicationId(applicationId)
                .orElseGet(ReferralSurveyResponse::new);
        response.setRespondent(respondent);
        response.setApplication(application);
        response.setDonation(null);
        response.setReferralSource(normalizeSource(source));
        response.setSubmittedAt(LocalDateTime.now());
        surveyRepository.save(response);
    }

    @Transactional
    public void submitForDonation(long donationId, String userEmail, String source) {
        var donation = donationRepository.findByIdWithDonor(donationId)
                .orElseThrow(() -> notFound("Donation was not found."));
        requireOwner(donation.getDonor().getEmail(), userEmail);
        var respondent = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> notFound("Account was not found."));
        ReferralSurveyResponse response = surveyRepository.findByDonationId(donationId)
                .orElseGet(ReferralSurveyResponse::new);
        response.setRespondent(respondent);
        response.setDonation(donation);
        response.setApplication(null);
        response.setReferralSource(normalizeSource(source));
        response.setSubmittedAt(LocalDateTime.now());
        surveyRepository.save(response);
    }

    @Transactional(readOnly = true)
    public boolean canAnswerApplication(long applicationId, String userEmail) {
        return applicationRepository.findById(applicationId)
                .filter(application -> application.getApplicant().getEmail().equalsIgnoreCase(userEmail))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canAnswerDonation(long donationId, String userEmail) {
        return donationRepository.findById(donationId)
                .filter(donation -> donation.getDonor().getEmail().equalsIgnoreCase(userEmail))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public List<ReferralSourceCount> analytics() {
        return surveyRepository.countResponsesByReferralSource().stream()
                .map(row -> new ReferralSourceCount(
                        String.valueOf(row[0]), SOURCES.getOrDefault(String.valueOf(row[0]), "Other"),
                        ((Number) row[1]).longValue()))
                .sorted(Comparator.comparingLong(ReferralSourceCount::count).reversed()
                        .thenComparing(ReferralSourceCount::label))
                .toList();
    }

    public static List<ReferralSourceOption> options() {
        return SOURCES.entrySet().stream()
                .map(entry -> new ReferralSourceOption(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ReferralSourceOption::label))
                .toList();
    }

    private static String normalizeSource(String source) {
        String normalized = source == null ? "" : source.trim().toUpperCase(Locale.ROOT);
        if (!SOURCES.containsKey(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose one of the listed ways you heard about us.");
        }
        return normalized;
    }

    private static void requireOwner(String ownerEmail, String authenticatedEmail) {
        if (ownerEmail == null || authenticatedEmail == null
                || !ownerEmail.equalsIgnoreCase(authenticatedEmail)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Submission was not found.");
        }
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    public record ReferralSourceOption(String value, String label) { }
    public record ReferralSourceCount(String source, String label, long count) { }
}
