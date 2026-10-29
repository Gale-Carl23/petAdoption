
package com.petdonation;

import com.petdonation.model.Pet;
import com.petdonation.service.PetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
public class AdminPetController {

    private final PetService petService;

    public AdminPetController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/admin/pets")
    public String listPets(Model model) {
        model.addAttribute("pets", petService.getAllPets());
        return "admin/pets";
    }

    @GetMapping("/admin/pets/new")
    public String showCreateForm(Model model) {
        model.addAttribute("pet", new Pet());
        model.addAttribute("pageTitle", "Add New Pet");
        model.addAttribute("formAction", "/admin/pets/new");
        return "admin/pet-form";
    }

    @PostMapping("/admin/pets/new")
    public String createPet(
            @Valid @ModelAttribute("pet") Pet pet,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Add New Pet");
            model.addAttribute("formAction", "/admin/pets/new");
            return "admin/pet-form";
        }

        petService.createPet(pet);
        redirectAttributes.addFlashAttribute(
                "successMessage", "Pet added successfully.");

        return "redirect:/admin/pets";
    }

    @GetMapping("/admin/pets/{id}/edit")
    public String showEditForm(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        Pet pet = petService.getPetById(id).orElse(null);

        if (pet == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Pet not found.");
            return "redirect:/admin/pets";
        }

        model.addAttribute("pet", pet);
        model.addAttribute("pageTitle", "Edit Pet");
        model.addAttribute("formAction", "/admin/pets/" + id + "/edit");

        return "admin/pet-form";
    }

    @PostMapping("/admin/pets/{id}/edit")
    public String updatePet(
            @PathVariable Long id,
            @Valid @ModelAttribute("pet") Pet pet,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Edit Pet");
            model.addAttribute("formAction", "/admin/pets/" + id + "/edit");
            return "admin/pet-form";
        }

        if (petService.updatePet(id, pet).isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Pet not found.");
            return "redirect:/admin/pets";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage", "Pet updated successfully.");

        return "redirect:/admin/pets";
    }

    @PostMapping("/admin/pets/{id}/availability")
    public String updateAvailability(@PathVariable Long id,
                                    @RequestParam String status,
                                    RedirectAttributes redirectAttributes) {
        try {
            if (petService.updateAvailability(id, status).isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Pet not found.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Pet availability updated.");
            }
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getReason());
        }
        return "redirect:/admin/pets";
    }
}
