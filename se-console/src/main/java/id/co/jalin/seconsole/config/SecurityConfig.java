package id.co.jalin.seconsole.config;

import id.co.jalin.seconsole.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Phase 2 (Auth) security config. REPLACES the permit-all bootstrap config.
 *
 * - Stateless (JWT only, no HTTP session)
 * - CSRF disabled (JWT in Authorization header, not cookies)
 * - Whitelist: login, change-password, health, static UI and SPA routes (handled by WebMvcConfig)
 * - Everything under /api/** and /actuator/** requires authentication
 * - @PreAuthorize enabled for role checks at method level (future use)
 *
 * BCrypt cost factor 12 per Foundation 9.2.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})  // default; same-origin in prod, Vite proxy in dev
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"error\":\"unauthorized\",\"code\":\"UNAUTHORIZED\"}");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write(
                                    "{\"error\":\"forbidden\",\"code\":\"FORBIDDEN\"}");
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Public API
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()

                        // Static UI: the WebMvcConfig forwards SPA_ROUTES to index.html.
                        // The actual static assets served by Spring are at root-level paths.
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.GET, "/assets/**", "/static/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/login", "/change-password").permitAll()  // SPA routes

                        // Everything else that is an API requires authentication
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/actuator/**").authenticated()

                        // Default allow (covers any extension-based static assets that WebMvcConfig handles)
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
