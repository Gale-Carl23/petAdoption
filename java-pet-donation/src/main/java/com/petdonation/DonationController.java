
package com.petdonation;

import com.petdonation.model.Donation;
import com.petdonation.model.User;
import com.petdonation.repository.UserRepository;
import com.petdonation.service.DonationProofStorageService;
import com.petdonation.service.DonationService;
import com.petdonation.service.DonationPaymentSettings;
import com.petdonation.service.ReferralSurveyService;
import com.petdonation.service.PayMongoCheckoutService;
import com.petdonation.service.PayMongoWebhookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class DonationController {

    private final DonationService donationService;
    private final UserRepository userRepository;
    private final DonationProofStorageService proofStorageService;
    private final ReferralSurveyService referralSurveyService;
    private final DonationPaymentSettings paymentSettings;
    private final PayMongoCheckoutService payMongoCheckoutService;
    private final PayMongoWebhookService payMongoWebhookService;

    public DonationController(
            DonationService donationService,
            UserRepository userRepository,
            DonationProofStorageService proofStorageService,
            ReferralSurveyService referralSurveyService,
            DonationPaymentSettings paymentSettings,
            PayMongoCheckoutService payMongoCheckoutService,
            PayMongoWebhookService payMongoWebhookService) {
        this.donationService = donationService;
        this.userRepository = userRepository;
        this.proofStorageService = proofStorageService;
        this.referralSurveyService = referralSurveyService;
        this.paymentSettings = paymentSettings;
        this.payMongoCheckoutService = payMongoCheckoutService;
        this.payMongoWebhookService = payMongoWebhookService;
    }

    @GetMapping("/donations")
    public String donations(Authentication authentication, Model model) {
        boolean administrator = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        model.addAttribute("administrator", administrator);
        var options = paymentSettings.options();
        model.addAttribute("paymentOptions", options);
        model.addAttribute("onlinePaymentsEnabled", options.stream().anyMatch(DonationPaymentSettings.PaymentOption::onlineCheckout));
        return "donations";
    }

    @GetMapping("/donations/form")
    public String showDonationForm(Model model) {
        model.addAttribute("donationForm", new DonationForm());
        model.addAttribute("paymentOptions", paymentSettings.options());
        return "donation-form";
    }

    @PostMapping("/donations/submit")
    public String submitDonation(
            @Valid @ModelAttribute("donationForm") DonationForm form,
            BindingResult result,
            @RequestParam(value = "proof", required = false)
            MultipartFile proof,
            Authentication authentication,
            Model model) {

        if (result.hasErrors()) {
            model.addAttribute("paymentOptions", paymentSettings.options());
            return "donation-form";
        }

        if (!paymentSettings.isConfiguredMethod(form.getPaymentMethod())) {
            model.addAttribute("paymentOptions", paymentSettings.options());
            model.addAttribute("paymentUnavailable", true);
            return "donation-form";
        }

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName().equals("anonymousUser")) {
            return "redirect:/login";
        }

        if ("PAYMONGO_QR".equals(form.getPaymentMethod())) {
            if (form.getAmount().compareTo(new BigDecimal("1.00")) < 0) {
                model.addAttribute("paymentOptions", paymentSettings.options());
                model.addAttribute("paymentUnavailable", true);
                model.addAttribute("paymentError", "QR Ph donations must be at least ₱1.00.");
                return "donation-form";
            }
            if (!payMongoCheckoutService.isConfigured()) {
                model.addAttribute("paymentOptions", paymentSettings.options());
                model.addAttribute("paymentUnavailable", true);
                return "donation-form";
            }
            User donor = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("Authenticated account not found."));
            Donation onlineDonation = new Donation();
            onlineDonation.setDonor(donor);
            onlineDonation.setFullName(form.getFullName() == null || form.getFullName().isBlank()
                    ? null : form.getFullName().trim());
            onlineDonation.setAmount(form.getAmount());
            onlineDonation.setDateSent(LocalDate.now());
            onlineDonation.setPaymentMethod("PAYMONGO_QR");
            onlineDonation.setMessage(form.getMessage() == null || form.getMessage().isBlank()
                    ? null : form.getMessage().trim());
            onlineDonation.setStatus("PAYMENT_PENDING");
            onlineDonation.setSubmittedAt(LocalDateTime.now());
            onlineDonation.setPaymentIdempotencyKey(UUID.randomUUID().toString());
            onlineDonation = donationService.saveDonation(onlineDonation);
            try {
                String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
                String checkoutUrl = payMongoCheckoutService.createQrCheckout(onlineDonation, baseUrl);
                donationService.saveDonation(onlineDonation);
                return "redirect:" + checkoutUrl;
            } catch (IOException | InterruptedException | RuntimeException exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                onlineDonation.setStatus("PAYMENT_FAILED");
                donationService.saveDonation(onlineDonation);
                model.addAttribute("paymentOptions", paymentSettings.options());
                model.addAttribute("paymentUnavailable", true);
                model.addAttribute("paymentError", "Secure checkout could not be started. No payment was taken. Please try again.");
                return "donation-form";
            }
        }

        if (form.getDateSent() == null) {
            model.addAttribute("paymentOptions", paymentSettings.options());
            model.addAttribute("proofError", "Date sent is required for manual transfers.");
            return "donation-form";
        }

        if (proof == null || proof.isEmpty()) {
            model.addAttribute(
                    "proofError",
                    "Proof of donation is required.");
            model.addAttribute("paymentOptions", paymentSettings.options());
            return "donation-form";
        }

        User donor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated account not found."));

        final String storedFilename;

        try {
            storedFilename = proofStorageService.store(proof);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("proofError", ex.getMessage());
            model.addAttribute("paymentOptions", paymentSettings.options());
            return "donation-form";
        } catch (IOException ex) {
            model.addAttribute(
                    "proofError",
                    "The proof file could not be saved. Please try again.");
            model.addAttribute("paymentOptions", paymentSettings.options());
            return "donation-form";
        }

        Donation donation = new Donation();
        donation.setDonor(donor);

        String fullName = form.getFullName();
        donation.setFullName(
                fullName == null || fullName.isBlank()
                        ? null
                        : fullName.trim());

        donation.setAmount(form.getAmount());
        donation.setDateSent(form.getDateSent());
        donation.setProofFilePath(storedFilename);
        donation.setPaymentMethod(form.getPaymentMethod());
        String paymentReference = form.getPaymentReference();
        donation.setPaymentReference(paymentReference == null || paymentReference.isBlank()
                ? null : paymentReference.trim());

        String message = form.getMessage();
        donation.setMessage(
                message == null || message.isBlank()
                        ? null
                        : message.trim());

        donation.setStatus("PENDING_VERIFICATION");
        donation.setSubmittedAt(LocalDateTime.now());

        // Save the donation before redirecting.
        donation = donationService.saveDonation(donation);

        return "redirect:/donations/thank-you?donationId=" + donation.getId();
    }

    @GetMapping("/donations/payment-result")
    public String paymentResult(@RequestParam Long id, Authentication authentication, Model model) {
        User donor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated account not found."));
        Donation donation = donationService.getDonationsByDonor(donor.getId()).stream()
                .filter(item -> item.getId().equals(id)).findFirst().orElse(null);
        if (donation == null || !"PAYMONGO_QR".equals(donation.getPaymentMethod())) return "redirect:/donations/my-donations";
        if ("PAYMENT_PENDING".equals(donation.getStatus()) && donation.getPaymongoSessionId() != null) {
            try {
                var session = payMongoCheckoutService.retrieveCheckout(donation.getPaymongoSessionId());
                payMongoWebhookService.reconcileCheckout(donation.getId(), session);
                donation = donationService.getDonationsByDonor(donor.getId()).stream()
                        .filter(item -> item.getId().equals(id)).findFirst().orElse(donation);
            } catch (IOException | InterruptedException | RuntimeException ignored) {
                if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
        model.addAttribute("donation", donation);
        return "donation-payment-result";
    }

    @GetMapping("/donations/thank-you")
    public String thankYou(
            @RequestParam(required = false) Long donationId,
            @RequestParam(required = false) String survey,
            Authentication authentication,
            Model model) {
        boolean surveyAvailable = donationId != null
                && authentication != null
                && referralSurveyService.canAnswerDonation(donationId, authentication.getName());
        model.addAttribute("surveyAvailable", surveyAvailable);
        model.addAttribute("donationId", donationId);
        model.addAttribute("surveySubmitted", "sent".equals(survey));
        model.addAttribute("surveyOptions", ReferralSurveyService.options());
        return "donation-thank-you";
    }

    @PostMapping("/donations/{donationId}/survey")
    public String submitDonationSurvey(
            @PathVariable Long donationId,
            @RequestParam String source,
            Authentication authentication) {
        referralSurveyService.submitForDonation(donationId, authentication.getName(), source);
        return "redirect:/donations/thank-you?donationId=" + donationId + "&survey=sent";
    }

    @GetMapping("/donations/my-donations")
    public String myDonations(Authentication authentication, Model model) {
        User donor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated account not found."));
        model.addAttribute("donations", donationService.getDonationsByDonor(donor.getId()));
        return "my-donations";
    }

    public static class DonationForm {

        @Size(max = 100,
                message = "Name must not exceed 100 characters.")
        private String fullName;

        @NotBlank(message = "Choose the payment method you used.")
        @Pattern(regexp = "GCASH|BANK_TRANSFER|QR_PH|PAYMONGO_QR",
                message = "Choose one of the listed payment methods.")
        private String paymentMethod;

        @Size(max = 100, message = "Payment reference must not exceed 100 characters.")
        private String paymentReference;

        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01",
                message = "Amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2,
                message = "Amount can have at most 2 decimal places.")
        private BigDecimal amount;

        @PastOrPresent(message = "Date sent cannot be in the future.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateSent;

        @Size(max = 1000,
                message = "Message must not exceed 1000 characters.")
        private String message;

        public String getFullName() {
            return fullName;
        }

        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getPaymentReference() { return paymentReference; }
        public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public LocalDate getDateSent() {
            return dateSent;
        }

        public void setDateSent(LocalDate dateSent) {
            this.dateSent = dateSent;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
