package com.stocktrading.simulator.config;

import com.stocktrading.simulator.service.EmailVerificationGate;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, EmailVerificationGate verificationGate) throws Exception {
        // Expose CSRF token in a cookie so client JS can read it
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/user/**",
                    "/api/wallet/**",
                    "/api/orders/**",
                    "/api/trades/**",
                    "/api/portfolio/**",
                    "/api/reports/**",
                    "/api/payments/**"
                ).authenticated()
                .requestMatchers("/api/stocks/**").permitAll()
                .anyRequest().permitAll() // Allow static resources, health, and auth endpoints
            )
            .exceptionHandling(exc -> exc
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> {
                    // Runs only after password matched; blocks unverified accounts only when enforcement is enabled.
                    if (!verificationGate.isLoginAllowed(auth.getName())) {
                        SecurityContext empty = SecurityContextHolder.createEmptyContext();
                        SecurityContextHolder.setContext(empty);
                        new RequestAttributeSecurityContextRepository().saveContext(empty, req, res);
                        HttpSession session = req.getSession(false);
                        if (session != null) {
                            session.invalidate();
                        }
                        res.setStatus(HttpStatus.FORBIDDEN.value());
                        return;
                    }
                    res.setStatus(HttpStatus.OK.value());
                })
                .failureHandler((req, res, exc) -> res.setStatus(HttpStatus.UNAUTHORIZED.value()))
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.OK.value()))
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
