package com.moveinsync.smart_booking_engine.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal. {@code employeeId} is the linked employee profile;
 * null for ADMIN/APPROVER accounts without one.
 */
public class CustomUserDetails implements UserDetails {

    private final UUID id;
    private final String username;
    private final String passwordHash;
    private final String role;
    private final UUID employeeId;
    private final boolean enabled;

    public CustomUserDetails(UUID id, String username, String passwordHash,
                             String role, UUID employeeId, boolean enabled) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.employeeId = employeeId;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
