package com.company.batchmonitor.api.service.impl;

import com.company.batchmonitor.api.dto.LoginRequest;
import com.company.batchmonitor.api.dto.LoginResponse;
import com.company.batchmonitor.api.dto.RegisterRequest;
import com.company.batchmonitor.api.service.AuthService;
import com.company.batchmonitor.domain.Role;
import com.company.batchmonitor.domain.User;
import com.company.batchmonitor.global.config.JwtTokenProvider;
import com.company.batchmonitor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 아이디입니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // 패스워드 불일치
            throw new IllegalArgumentException("잘못된 비밀번호입니다.");
        }

        // JWT 토큰 생성
        String token = jwtTokenProvider.createToken(user.getUsername(), user.getRole().name());

        return new LoginResponse(token, user.getUsername(), user.getName(), user.getRole().name());
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        Role userRole;
        try {
            userRole = Role.valueOf(request.getRole() != null ? request.getRole() : "ROLE_USER");
        } catch (IllegalArgumentException e) {
            userRole = Role.ROLE_USER;
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(userRole)
                .build();

        userRepository.save(user);
    }
}
