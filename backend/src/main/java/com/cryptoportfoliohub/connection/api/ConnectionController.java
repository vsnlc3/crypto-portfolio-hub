package com.cryptoportfoliohub.connection.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import com.cryptoportfoliohub.connection.application.ConnectionService;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.security.CurrentUserService;

@RestController
@RequestMapping("/api/v1/connections")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final CurrentUserService currentUserService;

    public ConnectionController(ConnectionService connectionService, CurrentUserService currentUserService) {
        this.connectionService = connectionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<ConnectionResponse> list(@AuthenticationPrincipal OidcUser principal) {
        User authenticatedUser = currentUserService.requireUser(principal);
        return connectionService.list(authenticatedUser);
    }

    @PostMapping
    public ResponseEntity<ConnectionResponse> create(
            @AuthenticationPrincipal OidcUser principal,
            @Valid @RequestBody ConnectionCreateRequest request) {
        User authenticatedUser = currentUserService.requireUser(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(connectionService.create(authenticatedUser, request));
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID connectionId) {
        User authenticatedUser = currentUserService.requireUser(principal);
        connectionService.delete(connectionId, authenticatedUser);
        return ResponseEntity.noContent().build();
    }
}
