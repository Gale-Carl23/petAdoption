
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

                pet.setName("Korgi the Brownie");
                pet.setBreed("Corgi");
                pet.setAge(1);
                pet.setDescription(
                    "A friendly dog looking for a loving home."
                );
                pet.setImageUrl("/images/figma-pets/korgi-card.png");
                pet.setStatus("AVAILABLE");

                petRepository.save(pet);

                Pet chow = new Pet();
                chow.setName("Chow the Brownie");
                chow.setBreed("Mixed Breed");
                chow.setAge(2);
                chow.setDescription("A gentle companion ready to meet a loving family.");
                chow.setImageUrl("/images/figma-pets/chow-card.png");
                chow.setStatus("AVAILABLE");
                petRepository.save(chow);

                Pet chi = new Pet();
                chi.setName("Chi the Brownie");
                chi.setBreed("Chihuahua Mix");
                chi.setAge(3);
                chi.setDescription("A bright, affectionate dog looking for a forever home.");
                chi.setImageUrl("/images/figma-pets/chi-card.png");
                chi.setStatus("AVAILABLE");
                petRepository.save(chi);

                Pet pug = new Pet();
                pug.setName("Pug the Brownie");
                pug.setBreed("Pug");
                pug.setAge(4);
                pug.setDescription("A cheerful dog hoping to find a caring family.");
                pug.setImageUrl("/images/figma-pets/pug-card.png");
                pug.setStatus("AVAILABLE");
                petRepository.save(pug);

                Pet husk = new Pet();
                husk.setName("Husk the Brownie");
                husk.setBreed("Siberian Husky");
                husk.setAge(5);
                husk.setDescription("A kind, energetic companion ready for a forever home.");
                husk.setImageUrl("/images/figma-pets/husk-card.png");
                husk.setStatus("AVAILABLE");
                petRepository.save(husk);

                Pet goldi = new Pet();
                goldi.setName("Goldi the Brownie");
                goldi.setBreed("Golden Retriever");
                goldi.setAge(6);
                goldi.setDescription("A gentle friend waiting to meet a loving family.");
                goldi.setImageUrl("/images/figma-pets/goldi-card.png");
                goldi.setStatus("AVAILABLE");
                petRepository.save(goldi);

                System.out.println("Six sample pets added successfully.");
            }
        };
    }
}
