package com.cryptoportfoliohub.activity.api;

import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cryptoportfoliohub.activity.application.ActivitiesQueryService;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.security.CurrentUserService;

@Validated
@RestController
@RequestMapping("/api/v1/activities")
public class ActivitiesController {

    private final ActivitiesQueryService activitiesQueryService;
    private final CurrentUserService currentUserService;

    public ActivitiesController(
            ActivitiesQueryService activitiesQueryService,
            CurrentUserService currentUserService) {
        this.activitiesQueryService = activitiesQueryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ActivitiesResponse list(
            @AuthenticationPrincipal OidcUser principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        User authenticatedUser = currentUserService.requireUser(principal);
        UUID authenticatedUserId = authenticatedUser.getId();
        return activitiesQueryService.getActivities(authenticatedUserId, cursor, limit);
    }
}
