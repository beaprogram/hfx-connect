package com.hfxconnect.security;

import com.hfxconnect.user.Role;
import java.util.UUID;

/**
 * The authenticated caller's identity, attached to Spring Security's
 * {@code SecurityContext} for every request presenting a valid, current
 * Bearer access token. Deliberately minimal — no password hash, no
 * refresh-session state, no other {@code User} entity field that no
 * authorization decision in this project currently needs. See ADR-009.
 */
public record CurrentUserPrincipal(UUID userId, String email, Role role) {
}
