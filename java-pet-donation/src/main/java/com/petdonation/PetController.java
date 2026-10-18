
package com.petdonation;

import com.petdonation.model.Pet;
import com.petdonation.service.PetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class PetController {

    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/pets")
    public String listPets(Model model) {
        List<Pet> pets = petService.getAllPets();

        model.addAttribute("pets", pets);

        return "pets";
    }
}
