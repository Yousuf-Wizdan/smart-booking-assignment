package com.moveinsync.smart_booking_engine.security;

import com.moveinsync.smart_booking_engine.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads login accounts for Spring Security. */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return appUserRepository.findByUsername(username)
                .map(u -> new CustomUserDetails(
                        u.getId(),
                        u.getUsername(),
                        u.getPasswordHash(),
                        u.getRole().name(),
                        u.getEmployee() != null ? u.getEmployee().getId() : null,
                        u.isEnabled()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
