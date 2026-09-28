package com.cryptoportfoliohub.portfolio.api;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.portfolio.application.PortfolioSummaryQueryService;
import com.cryptoportfoliohub.security.CurrentUserService;

@RestController
@RequestMapping("/api/v1/portfolio/summary")
public class PortfolioSummaryController {

    private final PortfolioSummaryQueryService queryService;
    private final CurrentUserService currentUserService;

    public PortfolioSummaryController(
            PortfolioSummaryQueryService queryService, CurrentUserService currentUserService) {
        this.queryService = queryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PortfolioSummaryResponse getSummary(@AuthenticationPrincipal OidcUser principal) {
        User authenticatedUser = currentUserService.requireUser(principal);
        UUID authenticatedUserId = authenticatedUser.getId();
        return queryService.getSummary(authenticatedUserId);
    }
}
