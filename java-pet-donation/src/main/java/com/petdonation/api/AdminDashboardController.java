
package com.petdonation.api;

import com.petdonation.repository.PetRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final PetRepository petRepository;

    public AdminDashboardController(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    @GetMapping("/summary")
    public Map<String, Long> getSummary() {
        long totalPets = petRepository.count();

        return Map.of("totalPets", totalPets);
    }
}
