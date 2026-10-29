
package com.petdonation.service;

import com.petdonation.model.Pet;
import com.petdonation.repository.PetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class PetService {

    private final PetRepository petRepository;

    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    public List<Pet> getAllPets() {
        return petRepository.findAll();
    }

    public Optional<Pet> getPetById(Long id) {
        return petRepository.findById(id);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Pet createPet(Pet pet) {
        pet.setId(null);
        pet.setStatus("AVAILABLE");

        return petRepository.save(pet);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Pet> updatePet(Long id, Pet updatedPet) {
        return petRepository.findById(id)
                .map(existingPet -> {
                    existingPet.setName(updatedPet.getName());
                    existingPet.setBreed(updatedPet.getBreed());
                    existingPet.setAge(updatedPet.getAge());
                    existingPet.setDescription(updatedPet.getDescription());
                    existingPet.setImageUrl(updatedPet.getImageUrl());

                    return petRepository.save(existingPet);
                });
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Pet> updateAvailability(Long id, String requestedStatus) {
        String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("AVAILABLE", "RESERVED", "UNAVAILABLE").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose available, reserved, or unavailable. Adoption approval sets adopted status automatically.");
        }
        return petRepository.findByIdForUpdate(id).map(pet -> {
            if ("ADOPTED".equals(pet.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This pet has an approved adopter. Update the adoption decision before changing its availability.");
            }
            pet.setStatus(status);
            return petRepository.save(pet);
        });
    }
}
