package com.digitalgrader.config;

import java.util.Arrays;
import java.util.List;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.digitalgrader.auth.JwtFilter;
import com.digitalgrader.auth.TokenService;
import com.digitalgrader.common.ApiErrors;
import com.digitalgrader.users.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens, UserRepository users,
            ObjectMapper mapper, UrlBasedCorsConfigurationSource cors) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(config -> config.configurationSource(cors))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/cohorts/mine").authenticated()
                        .requestMatchers("/api/v1/cohorts", "/api/v1/cohorts/**").hasAnyRole("INSTRUCTOR", "ADMIN")
                        .requestMatchers("/api/v1/assignments", "/api/v1/assignments/**", "/api/v1/submissions", "/api/v1/submissions/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> ApiErrors.write(mapper, response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, error) -> ApiErrors.write(mapper, response, HttpStatus.FORBIDDEN)))
                .addFilterBefore(new JwtFilter(tokens, users, mapper), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGINS:http://127.0.0.1:5176}") String origins) {
        var allowed = Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        for (String origin : allowed) {
            URI uri = URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty()))
                throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS must contain exact HTTP(S) origins");
        }
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowed);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
