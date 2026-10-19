
package com.petdonation;

import com.petdonation.service.PetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final PetService petService;

    public HomeController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pets", petService.getAllPets());
        return "home";
    }

    @GetMapping("/layout-test")
    public String layoutTest() {
        return "layout-test";
    }
}
