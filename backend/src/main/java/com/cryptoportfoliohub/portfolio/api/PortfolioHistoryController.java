package com.cryptoportfoliohub.portfolio.api;

import java.util.UUID;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.portfolio.application.PortfolioHistoryPeriod;
import com.cryptoportfoliohub.portfolio.application.PortfolioHistoryQueryService;
import com.cryptoportfoliohub.security.CurrentUserService;

@Validated
@RestController
@RequestMapping("/api/v1/portfolio/history")
public class PortfolioHistoryController {

    private final PortfolioHistoryQueryService queryService;
    private final CurrentUserService currentUserService;

    public PortfolioHistoryController(
            PortfolioHistoryQueryService queryService, CurrentUserService currentUserService) {
        this.queryService = queryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PortfolioHistoryResponse history(
            @AuthenticationPrincipal OidcUser principal,
            @RequestParam(defaultValue = "7D") @Pattern(regexp = "7D|30D|90D|1Y") String period) {
        User authenticatedUser = currentUserService.requireUser(principal);
        UUID authenticatedUserId = authenticatedUser.getId();
        return queryService.getHistory(authenticatedUserId, period(period));
    }

    private PortfolioHistoryPeriod period(String value) {
        return switch (value) {
            case "7D" -> PortfolioHistoryPeriod.SEVEN_DAYS;
            case "30D" -> PortfolioHistoryPeriod.THIRTY_DAYS;
            case "90D" -> PortfolioHistoryPeriod.NINETY_DAYS;
            case "1Y" -> PortfolioHistoryPeriod.ONE_YEAR;
            default -> throw new IllegalArgumentException("Unsupported portfolio history period.");
        };
    }
}
