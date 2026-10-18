
package com.petdonation;

import com.petdonation.model.Donation;
import com.petdonation.model.User;
import com.petdonation.repository.UserRepository;
import com.petdonation.service.DonationProofStorageService;
import com.petdonation.service.DonationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
public class DonationController {

    private final DonationService donationService;
    private final UserRepository userRepository;
    private final DonationProofStorageService proofStorageService;

    public DonationController(
            DonationService donationService,
            UserRepository userRepository,
            DonationProofStorageService proofStorageService) {
        this.donationService = donationService;
        this.userRepository = userRepository;
        this.proofStorageService = proofStorageService;
    }

    @GetMapping("/donations")
    public String donations() {
        return "donations";
    }

    @GetMapping("/donations/form")
    public String showDonationForm(Model model) {
        model.addAttribute("donationForm", new DonationForm());
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
            return "donation-form";
        }

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName().equals("anonymousUser")) {
            return "redirect:/login";
        }

        if (proof == null || proof.isEmpty()) {
            model.addAttribute(
                    "proofError",
                    "Proof of donation is required.");
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
            return "donation-form";
        } catch (IOException ex) {
            model.addAttribute(
                    "proofError",
                    "The proof file could not be saved. Please try again.");
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

        String message = form.getMessage();
        donation.setMessage(
                message == null || message.isBlank()
                        ? null
                        : message.trim());

        donation.setStatus("PENDING_VERIFICATION");
        donation.setSubmittedAt(LocalDateTime.now());

        // Save the donation before redirecting.
        donationService.saveDonation(donation);

        return "redirect:/donations/thank-you";
    }

    @GetMapping("/donations/thank-you")
    public String thankYou() {
        return "donation-thank-you";
    }

    public static class DonationForm {

        @Size(max = 100,
                message = "Name must not exceed 100 characters.")
        private String fullName;

        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01",
                message = "Amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2,
                message = "Amount can have at most 2 decimal places.")
        private BigDecimal amount;

        @NotNull(message = "Date sent is required.")
        @PastOrPresent(message = "Date sent cannot be in the future.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateSent;

        @Size(max = 1000,
                message = "Message must not exceed 1000 characters.")
        private String message;

        public String getFullName() {
            return fullName;
        }

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
