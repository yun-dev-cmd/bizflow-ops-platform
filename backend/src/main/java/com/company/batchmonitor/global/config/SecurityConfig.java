package com.company.batchmonitor.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 로그인 & 회원가입
                .requestMatchers("/api/auth/**").permitAll()
                // 정적 파일 및 대시보드 UI
                .requestMatchers("/", "/index.html", "/static/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                // Swagger UI & API docs
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                // 외부 연계 Mock API
                .requestMatchers("/api/external-results/mock").permitAll()
                .requestMatchers("/api/external-results").hasAnyRole("OPERATOR", "ADMIN")
                // 대시보드 요약 정보
                .requestMatchers("/api/dashboard/summary").hasAnyRole("USER", "OPERATOR", "ADMIN")
                // 정산 요청 및 첨부파일 API
                .requestMatchers("/api/settlements/**").hasAnyRole("USER", "OPERATOR", "ADMIN")
                .requestMatchers("/api/files/**").hasAnyRole("USER", "OPERATOR", "ADMIN")
                // 배치 실행 및 결과
                .requestMatchers("/api/batches/reconciliation/run").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers("/api/batches/reconciliation/retry").hasRole("ADMIN")
                .requestMatchers("/api/batches/logs").hasAnyRole("OPERATOR", "ADMIN")
                .requestMatchers("/api/reconciliation-results").hasAnyRole("OPERATOR", "ADMIN")
                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
