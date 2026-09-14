package ru.sportsresults.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(AdminSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/**", "/api/results/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/result-issue-attachments/capabilities").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/events/*/result-inquiry/verify").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/events/*/result-issue-requests/missing",
                                "/api/events/*/results/*/result-issue-requests").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/result-issues/*/attachments",
                                "/api/result-issues/*/attachments/*/confirm",
                                "/api/result-issues/*/attachments/*/authorize").permitAll()
                        .requestMatchers(HttpMethod.GET, "/share/attachments/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"status\":403,\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}"
                            );
                        })
                );
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(AdminSecurityProperties properties, PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(User.withUsername(properties.username())
                .password(passwordEncoder.encode(properties.password()))
                .roles("ADMIN")
                .build());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
