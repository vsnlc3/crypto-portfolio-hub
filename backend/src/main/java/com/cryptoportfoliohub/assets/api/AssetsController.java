package com.cryptoportfoliohub.assets.api;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cryptoportfoliohub.assets.application.AssetsQueryService;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.security.CurrentUserService;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetsController {

    private final AssetsQueryService assetsQueryService;
    private final CurrentUserService currentUserService;

    public AssetsController(AssetsQueryService assetsQueryService, CurrentUserService currentUserService) {
        this.assetsQueryService = assetsQueryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public AssetsResponse list(@AuthenticationPrincipal OidcUser principal) {
        User authenticatedUser = currentUserService.requireUser(principal);
        UUID authenticatedUserId = authenticatedUser.getId();
        return assetsQueryService.getAssets(authenticatedUserId);
    }
}
