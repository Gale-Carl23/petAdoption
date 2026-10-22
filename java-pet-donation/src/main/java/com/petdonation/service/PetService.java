
package com.petdonation.service;

import com.petdonation.model.Pet;
import com.petdonation.repository.PetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Pet createPet(Pet pet) {
        pet.setId(null);
        pet.setStatus("AVAILABLE");

        return petRepository.save(pet);
    }

    @Transactional
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
}
