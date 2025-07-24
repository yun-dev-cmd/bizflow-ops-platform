package com.company.batchmonitor.api.service;

import com.company.batchmonitor.api.dto.LoginRequest;
import com.company.batchmonitor.api.dto.LoginResponse;
import com.company.batchmonitor.api.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    void register(RegisterRequest request);
}
