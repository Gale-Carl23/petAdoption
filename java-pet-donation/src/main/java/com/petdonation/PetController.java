package com.petdonation;

import com.petdonation.model.Pet;
import com.petdonation.model.User;
import com.petdonation.repository.UserRepository;
import com.petdonation.service.AdoptionApplicationService;
import com.petdonation.service.PetService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PetController {

    private final PetService petService;
    private final AdoptionApplicationService applicationService;
    private final UserRepository userRepository;

    public PetController(
            PetService petService,
            AdoptionApplicationService applicationService,
            UserRepository userRepository) {
        this.petService = petService;
        this.applicationService = applicationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/pets")
    public String listPets(Model model) {
        model.addAttribute("pets", petService.getAllPets());
        return "pets";
    }

    @GetMapping("/pets/{id}")
    public String showPetDetails(
            @PathVariable Long id,
            Authentication authentication,
            Model model) {

        Pet pet = petService.getPetById(id).orElse(null);

        if (pet == null) {
            return "redirect:/pets";
        }

        model.addAttribute("pet", pet);
        model.addAttribute("hasApplied", false);

        if (authentication != null
                && authentication.isAuthenticated()
                && !authentication.getName().equals("anonymousUser")) {

            User applicant = userRepository
                    .findByEmail(authentication.getName())
                    .orElse(null);

            if (applicant != null) {
                boolean hasApplied =
                        applicationService.hasActiveApplication(
                                applicant.getId(),
                                pet.getId());

                model.addAttribute("hasApplied", hasApplied);
            }
        }

        return "pet-details";
    }
}
