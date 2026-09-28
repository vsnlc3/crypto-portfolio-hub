package com.cryptoportfoliohub.positions.api;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.positions.application.PositionsQueryService;
import com.cryptoportfoliohub.security.CurrentUserService;

@RestController
@RequestMapping("/api/v1/positions")
public class PositionsController {

    private final PositionsQueryService positionsQueryService;
    private final CurrentUserService currentUserService;

    public PositionsController(
            PositionsQueryService positionsQueryService, CurrentUserService currentUserService) {
        this.positionsQueryService = positionsQueryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PositionsResponse list(@AuthenticationPrincipal OidcUser principal) {
        User authenticatedUser = currentUserService.requireUser(principal);
        UUID authenticatedUserId = authenticatedUser.getId();
        return positionsQueryService.getPositions(authenticatedUserId);
    }
}
