package com.marketai.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── MUST BE FIRST: allow all CORS pre-flight OPTIONS requests ─
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ── Public: auth endpoints ────────────────────────────────────
                        .requestMatchers("/api/auth/**").permitAll()

                        // ── Public: websocket ─────────────────────────────────────────
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/ws").permitAll()

                        // ── Public: market data ───────────────────────────────────────
                        .requestMatchers("/api/crypto/**").permitAll()
                        .requestMatchers("/api/stocks/**").permitAll()
                        .requestMatchers("/api/market/**").permitAll()

                        // ── Public: analytics read-only ───────────────────────────────
                        .requestMatchers("/api/indicators/**").permitAll()
                        .requestMatchers("/api/risk/**").permitAll()
                        .requestMatchers("/api/confluence/**").permitAll()
                        .requestMatchers("/api/alerts/**").permitAll()
                        .requestMatchers("/api/news/**").permitAll()
                        .requestMatchers("/api/ai/latest").permitAll()

                        // ── Public: backtest reads ────────────────────────────────────
                        .requestMatchers("/api/backtest/all").permitAll()
                        .requestMatchers("/api/backtest/system-accuracy").permitAll()
                        .requestMatchers("/api/backtest/symbol/**").permitAll()

                        // ── Actuator: health/info public ──────────────────────────────
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/info").permitAll()

                        // ── Actuator: prometheus + rest → ADMIN only ──────────────────
                        // FIX: using hasAuthority("ROLE_ADMIN") instead of hasRole("ADMIN")
                        // because JwtFilter explicitly sets: new SimpleGrantedAuthority("ROLE_" + role)
                        // hasRole("ADMIN") should also work but hasAuthority is explicit and unambiguous
                        .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")

                        // ── Admin API ─────────────────────────────────────────────────
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")

                        // ── Everything else: must be logged in ────────────────────────
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://market-ai-dashboard.vercel.app"
    ));
  config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
