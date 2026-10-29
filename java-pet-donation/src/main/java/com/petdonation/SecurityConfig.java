
package com.petdonation;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import com.petdonation.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

@EnableMethodSecurity
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
                    "/access-denied",
                    "/donations",
                    "/api/auth/csrf",
                    "/actuator/health",
                    "/actuator/health/**",
                    "/livez",
                    "/readyz",
                    "/css/**",
                    "/images/**"
                ).permitAll()

                // Only ADMIN can manage pets.
                // These patterns reserve the management URL namespace.
                .requestMatchers(
                    "/api/admin/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                    "/admin/**"
                ).hasRole("ADMIN")

                // Donation and adoption records belong to supporter accounts.
                .requestMatchers(
                    "/donations/form",
                    "/donations/submit",
                    "/donations/thank-you",
                    "/donations/my-donations",
                    "/donations/*/survey",
                    "/adoptions/apply/**",
                    "/adoptions/applications/*/survey",
                    "/adoptions/my-applications",
                    "/adoptions/thank-you"
                ).hasRole("ADOPTER")

                // Any other route requires login by default.
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    boolean administrator = authentication.getAuthorities().contains(
                            new SimpleGrantedAuthority("ROLE_ADMIN"));
                    response.sendRedirect(administrator ? "/admin" : "/");
                })
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .exceptionHandling(exceptions -> exceptions
                    .accessDeniedPage("/access-denied"));

        return http.build();
    }
}
