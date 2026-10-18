
package com.petdonation;

import com.petdonation.model.Pet;
import com.petdonation.repository.PetRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner loadPetData(PetRepository petRepository) {
        return args -> {
            if (petRepository.count() == 0) {
                Pet pet = new Pet();

                pet.setName("Korgi");
                pet.setBreed("Corgi");
                pet.setAge(1);
                pet.setDescription(
                    "A friendly dog looking for a loving home."
                );
                pet.setImageUrl(null);
                pet.setStatus("AVAILABLE");

                petRepository.save(pet);

                System.out.println("Sample pet added successfully.");
            }
        };
    }
}
