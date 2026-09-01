package dev.nexus.config;

import dev.nexus.auth.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.CacheControlConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The only API paths reachable without a token. */
    private static final String[] PUBLIC_API_ENDPOINTS = {
        ApiPaths.PREFIX + "/auth/register",
        ApiPaths.PREFIX + "/auth/login",
        ApiPaths.PREFIX + "/auth/refresh",
        ApiPaths.PREFIX + "/auth/logout",
        ApiPaths.PREFIX + "/health"
    };

    private final NexusProperties properties;

    public SecurityConfig(NexusProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http.cors(Customizer.withDefaults())
                // No server-side session and no cookie-driven form posts: the refresh
                // cookie is SameSite=Strict, which is what stands in for CSRF tokens here.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_API_ENDPOINTS)
                        .permitAll()
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        // Anything else under /actuator would expose internals if it were
                        // ever added to the exposure list, so refuse it here too.
                        .requestMatchers("/actuator/**")
                        .denyAll()
                        // The API stays deny-by-default: everything not whitelisted above
                        // needs a token.
                        .requestMatchers("/api/**")
                        .authenticated()
                        // What remains is the built single-page app. Serving its shell has
                        // to be public or nobody could reach the login screen; it carries no
                        // data of its own, and every route behind it calls the API above.
                        .anyRequest()
                        .permitAll())
                /*
                 * Everything the API answers is a reader's own and must not be held anywhere,
                 * which is what Spring's default no-store is for — except the browse shelves,
                 * which are the same list for every reader and change by the day at most.
                 * Those say for themselves how long they may be kept; the rest keep no-store.
                 */
                .headers(headers -> headers.cacheControl(CacheControlConfig::disable)
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                new NegatedRequestMatcher(
                                        PathPatternRequestMatcher.withDefaults()
                                                .matcher(ApiPaths.PREFIX + "/catalog/browse")),
                                new CacheControlHeadersWriter())))
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = properties.security().allowedOrigins();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins == null ? List.of() : origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Required for the browser to send and store the refresh cookie cross-origin.
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
