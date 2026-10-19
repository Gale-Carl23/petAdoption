package com.petdonation;

import com.petdonation.model.AdoptionApplication;
import com.petdonation.model.Pet;
import com.petdonation.model.User;
import com.petdonation.repository.PetRepository;
import com.petdonation.repository.UserRepository;
import com.petdonation.service.AdoptionApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AdoptionController {

    private final AdoptionApplicationService applicationService;
    private final PetRepository petRepository;
    private final UserRepository userRepository;

    public AdoptionController(
            AdoptionApplicationService applicationService,
            PetRepository petRepository,
            UserRepository userRepository) {
        this.applicationService = applicationService;
        this.petRepository = petRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/adoptions/apply/{petId}")
    public String showApplicationForm(
            @PathVariable Long petId,
            Model model) {

        Pet pet = petRepository.findById(petId).orElse(null);

        if (pet == null) {
            return "redirect:/pets";
        }

        model.addAttribute("pet", pet);
        model.addAttribute("applicationForm", new ApplicationForm());

        return "adoption-form";
    }

    @PostMapping("/adoptions/apply/{petId}")
    public String submitApplication(
            @PathVariable Long petId,
            @Valid @ModelAttribute("applicationForm")
            ApplicationForm form,
            BindingResult result,
            Authentication authentication,
            Model model) {

        Pet pet = petRepository.findById(petId).orElse(null);

        if (pet == null) {
            return "redirect:/pets";
        }

        if (result.hasErrors()) {
            model.addAttribute("pet", pet);
            return "adoption-form";
        }

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName().equals("anonymousUser")) {
            return "redirect:/login";
        }

        User applicant = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated account not found."));

        AdoptionApplication application = new AdoptionApplication();
        application.setApplicant(applicant);
        application.setPet(pet);
        application.setReasonForAdoption(form.getReasonForAdoption().trim());
        application.setHousingType(form.getHousingType());
        application.setPetExperience(form.getPetExperience());

        try {
            applicationService.submitApplication(application);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("pet", pet);
            model.addAttribute("applicationError", ex.getMessage());
            return "adoption-form";
        }

        return "redirect:/adoptions/thank-you";
    }

    public static class ApplicationForm {

        @NotBlank(message = "Please explain why you want to adopt this pet.")
        @Size(max = 1000, message = "Your answer must not exceed 1000 characters.")
        private String reasonForAdoption;

        @NotBlank(message = "Please select your housing type.")
        private String housingType;

        @NotBlank(message = "Please select your pet experience.")
        private String petExperience;

        public String getReasonForAdoption() {
            return reasonForAdoption;
        }

        public void setReasonForAdoption(String reasonForAdoption) {
            this.reasonForAdoption = reasonForAdoption;
        }

        public String getHousingType() {
            return housingType;
        }

        public void setHousingType(String housingType) {
            this.housingType = housingType;
        }

        public String getPetExperience() {
            return petExperience;
        }

        public void setPetExperience(String petExperience) {
            this.petExperience = petExperience;
        }
    }
}