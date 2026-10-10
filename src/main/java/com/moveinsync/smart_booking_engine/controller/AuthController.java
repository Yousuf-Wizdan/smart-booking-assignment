package com.moveinsync.smart_booking_engine.controller;

import com.moveinsync.smart_booking_engine.dto.request.LoginRequest;
import com.moveinsync.smart_booking_engine.dto.request.RegisterRequest;
import com.moveinsync.smart_booking_engine.dto.response.MeResponse;
import com.moveinsync.smart_booking_engine.dto.response.TokenResponse;
import com.moveinsync.smart_booking_engine.security.AuthenticatedUser;
import com.moveinsync.smart_booking_engine.security.CustomUserDetails;
import com.moveinsync.smart_booking_engine.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        CustomUserDetails user = AuthenticatedUser.require();
        return ResponseEntity.ok(new MeResponse(user.getUsername(),
                com.moveinsync.smart_booking_engine.enums.Role.valueOf(user.getRole()),
                user.getEmployeeId()));
    }
}
