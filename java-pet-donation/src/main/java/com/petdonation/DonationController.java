
package com.petdonation;

import com.petdonation.model.Donation;
import com.petdonation.model.User;
import com.petdonation.repository.UserRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
public class DonationController {

    private final DonationService donationService;
    private final UserRepository userRepository;

    public DonationController(
            DonationService donationService,
            UserRepository userRepository) {
        this.donationService = donationService;
        this.userRepository = userRepository;
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
            @RequestParam("proof") MultipartFile proof,
            Authentication authentication,
            Model model) {

        if (result.hasErrors()) {
            return "donation-form";
        }

        if (proof == null || proof.isEmpty()) {
            model.addAttribute("proofError", "Proof of donation is required.");
            return "donation-form";
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User donor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalStateException("Authenticated account not found."));

        // File validation and secure storage will be implemented next.
        model.addAttribute("uploadPending", true);
        model.addAttribute("donationForm", form);
        return "donation-form";
    }

    public static class DonationForm {

        @Size(max = 100, message = "Name must not exceed 100 characters.")
        private String fullName;

        @NotNull(message = "Amount is required.")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2,
                message = "Amount can have at most 2 decimal places.")
        private BigDecimal amount;

        @NotNull(message = "Date sent is required.")
        @PastOrPresent(message = "Date sent cannot be in the future.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateSent;

        @Size(max = 1000, message = "Message must not exceed 1000 characters.")
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
