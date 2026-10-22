
package com.petdonation;

import com.petdonation.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider)
            throws Exception {

        http
            .authenticationProvider(authenticationProvider)
            .authorizeHttpRequests(auth -> auth

                // Public pages and static resources
                .requestMatchers(
                    "/",
                    "/about",
                    "/pets",
                    "/pets/**",
                    "/register",
                    "/login",
                    "/donations",
                    "/css/**",
                    "/images/**"
                ).permitAll()

                // Only ADMIN can manage pets.
                // These patterns reserve the management URL namespace.
                .requestMatchers(
                    "/admin/**"
                ).hasRole("ADMIN")

                // Authenticated adopter actions
                .requestMatchers(
                    "/donations/form",
                    "/donations/submit",
                    "/donations/thank-you",
                    "/adoptions/apply/**",
                    "/adoptions/my-applications",
                    "/adoptions/thank-you"
                ).authenticated()

                // Any other route requires login by default.
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }
}
