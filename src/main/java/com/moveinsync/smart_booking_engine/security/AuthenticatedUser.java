package com.moveinsync.smart_booking_engine.security;

import org.springframework.security.core.context.SecurityContextHolder;

/** Ownership helpers: the server derives identity from the principal, never from request ids. */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    public static CustomUserDetails current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) {
            return user;
        }
        return null;
    }

    public static CustomUserDetails require() {
        CustomUserDetails user = current();
        if (user == null) {
            throw new IllegalStateException("No authenticated user");
        }
        return user;
    }

    /** True when the caller may act on a booking owned by {@code ownerEmployeeId}. */
    public static boolean canAccessEmployeeData(CustomUserDetails user, java.util.UUID ownerEmployeeId) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        return ownerEmployeeId != null && ownerEmployeeId.equals(user.getEmployeeId());
    }
}
