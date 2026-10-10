package com.moveinsync.smart_booking_engine.service;

import com.moveinsync.smart_booking_engine.dto.request.LoginRequest;
import com.moveinsync.smart_booking_engine.dto.request.RegisterRequest;
import com.moveinsync.smart_booking_engine.dto.response.TokenResponse;
import com.moveinsync.smart_booking_engine.entity.AppUser;
import com.moveinsync.smart_booking_engine.entity.Employee;
import com.moveinsync.smart_booking_engine.enums.Role;
import com.moveinsync.smart_booking_engine.exception.ConflictException;
import com.moveinsync.smart_booking_engine.exception.ResourceNotFoundException;
import com.moveinsync.smart_booking_engine.repository.AppUserRepository;
import com.moveinsync.smart_booking_engine.repository.EmployeeRepository;
import com.moveinsync.smart_booking_engine.security.CustomUserDetails;
import com.moveinsync.smart_booking_engine.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login. Public self-registration always creates an
 * {@code EMPLOYEE} account; ADMIN/APPROVER accounts are created only by an
 * admin (or the env-var bootstrap, see {@code AdminBootstrap}).
 */
@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository, EmployeeRepository employeeRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (appUserRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username is already taken: " + request.username());
        }
        if (employeeRepository.existsByEmployeeCode(request.employeeCode())) {
            throw new ConflictException("Employee code already exists: " + request.employeeCode());
        }
        Employee employee = new Employee(request.employeeCode(), request.fullName(),
                request.businessUnit(), request.costCentreCode(), request.gender(),
                request.shiftStart(), request.shiftEnd());
        employeeRepository.save(employee);
        AppUser user = new AppUser(request.username(),
                passwordEncoder.encode(request.password()), Role.EMPLOYEE, employee, true);
        appUserRepository.save(user);
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!user.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return tokenFor(user);
    }

    private TokenResponse tokenFor(AppUser user) {
        CustomUserDetails principal = new CustomUserDetails(user.getId(), user.getUsername(),
                user.getPasswordHash(), user.getRole().name(),
                user.getEmployee() != null ? user.getEmployee().getId() : null,
                user.isEnabled());
        String token = jwtService.generateToken(principal);
        return new TokenResponse(token, "Bearer", user.getUsername(), user.getRole(),
                principal.getEmployeeId());
    }

    @Transactional(readOnly = true)
    public TokenResponse currentUserToken(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return tokenFor(user);
    }
}
