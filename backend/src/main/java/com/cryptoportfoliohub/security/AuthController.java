package com.cryptoportfoliohub.security;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cryptoportfoliohub.persistence.entity.User;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse currentUser(@AuthenticationPrincipal OidcUser principal) {
        User user = currentUserService.requireUser(principal);
        return new AuthenticatedUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getAvatarUrl());
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrfToken(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    public record AuthenticatedUserResponse(UUID id, String email, String displayName, String avatarUrl) {
        @Override
        public String toString() {
            return "AuthenticatedUserResponse[id=" + id + "]";
        }
    }

    public record CsrfTokenResponse(String headerName, String token) {
        @Override
        public String toString() {
            return "CsrfTokenResponse[redacted]";
        }
    }
}
