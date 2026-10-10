package com.moveinsync.smart_booking_engine.config;

import tools.jackson.databind.ObjectMapper;
import com.moveinsync.smart_booking_engine.exception.ApiError;
import com.moveinsync.smart_booking_engine.security.JwtAuthenticationFilter;
import com.moveinsync.smart_booking_engine.security.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. Public: auth endpoints and actuator health/info.
 * Everything else requires a valid Bearer token; finer role and ownership
 * checks live in the URL rules below and in the services.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   RequestIdFilter requestIdFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE);
                            objectMapper.writeValue(response.getWriter(), new ApiError(
                                    Instant.now(), 401, "UNAUTHORIZED",
                                    "Authentication required", request.getRequestURI(), List.of(),
                                    attribute == null ? UUID.randomUUID().toString()
                                            : attribute.toString()));
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE);
                            objectMapper.writeValue(response.getWriter(), new ApiError(
                                    Instant.now(), 403, "FORBIDDEN", "Access denied",
                                    request.getRequestURI(), List.of(),
                                    attribute == null ? UUID.randomUUID().toString()
                                            : attribute.toString()));
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/employees/**")
                        .hasAnyRole("EMPLOYEE", "ADMIN", "APPROVER")
                        .requestMatchers("/api/v1/employees/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/policies/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/quotas/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/approvals/**").hasAnyRole("APPROVER", "ADMIN")
                        .requestMatchers("/api/v1/bookings/**")
                        .hasAnyRole("EMPLOYEE", "ADMIN", "APPROVER")
                        .anyRequest().denyAll())
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
