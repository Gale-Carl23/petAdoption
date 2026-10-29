package com.petdonation.api;

import com.petdonation.model.Pet;
import com.petdonation.service.PetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/pets")
public class AdminPetApiController {

    private final PetService petService;

    public AdminPetApiController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping
    public List<PetResponse> listPets() {
        return petService.getAllPets().stream().map(PetResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<PetResponse> createPet(
            @Valid @RequestBody PetRequest request,
            UriComponentsBuilder uriBuilder) {
        Pet created = petService.createPet(request.toPet());
        URI location = uriBuilder.path("/api/admin/pets/{id}")
                .buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(PetResponse.from(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PetResponse> updatePet(
            @PathVariable Long id,
            @Valid @RequestBody PetRequest request) {
        return petService.updatePet(id, request.toPet())
                .map(PetResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record PetRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String breed,
            @NotNull @Min(0) Integer age,
            @Size(max = 1000) String description,
            @Size(max = 500) String imageUrl) {

        Pet toPet() {
            Pet pet = new Pet();
            pet.setName(name.trim());
            pet.setBreed(breed.trim());
            pet.setAge(age);
            pet.setDescription(description == null ? null : description.trim());
            pet.setImageUrl(imageUrl == null ? null : imageUrl.trim());
            return pet;
        }
    }

    public record PetResponse(
            Long id,
            String name,
            String breed,
            Integer age,
            String description,
            String imageUrl,
            String status) {

        static PetResponse from(Pet pet) {
            return new PetResponse(pet.getId(), pet.getName(), pet.getBreed(),
                    pet.getAge(), pet.getDescription(), pet.getImageUrl(), pet.getStatus());
        }
    }
}
