package com.cryptoportfoliohub;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import com.cryptoportfoliohub.persistence.entity.User;
import com.cryptoportfoliohub.persistence.repository.UserRepository;
import com.cryptoportfoliohub.portfolio.api.PortfolioHistoryResponse;
import com.cryptoportfoliohub.portfolio.api.PortfolioSummaryResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class PortfolioHistoryApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void returnsOnlyOwnedSnapshotsInAscendingOrderWithoutInterpolatingMissingPoints() throws Exception {
        User owner = createUser("history-owner");
        User other = createUser("history-other");
        Instant now = Instant.now().minusSeconds(2);
        Instant oldestVisible = now.minusSeconds(6 * 86_400);
        Instant newestVisible = now.minusSeconds(2 * 86_400);
        insertSnapshot(owner, oldestVisible, "100", "COMPLETE");
        insertSnapshot(owner, newestVisible, "200", "STALE");
        insertSnapshot(owner, now.minusSeconds(30 * 86_400), "300", "COMPLETE");
        insertSnapshot(other, now.minusSeconds(86_400), "999999", "COMPLETE");

        PortfolioHistoryResponse response = getHistory(owner, "7D");

        assertThat(response.period()).isEqualTo("7D");
        assertThat(response.status()).isEqualTo(PortfolioHistoryResponse.Status.AVAILABLE);
        assertThat(response.points()).hasSize(2);
        assertThat(response.points()).extracting(PortfolioHistoryResponse.Point::snapshotAt)
                .containsExactly(oldestVisible, newestVisible);
        assertThat(response.points()).extracting(PortfolioHistoryResponse.Point::netWorthJpy)
                .containsExactly(new BigDecimal("100.00000000"), new BigDecimal("200.00000000"));
        assertThat(response.points()).extracting(point -> point.status().name())
                .containsExactly("COMPLETE", "STALE");
    }

    @Test
    void returnsEmptyWhenTheSelectedPeriodHasNoSnapshotsAndRejectsUnsupportedPeriod() throws Exception {
        User owner = createUser("history-empty");
        insertSnapshot(owner, Instant.now().minusSeconds(370L * 86_400), "10", "COMPLETE");

        PortfolioHistoryResponse response = getHistory(owner, "1Y");

        assertThat(response.status()).isEqualTo(PortfolioHistoryResponse.Status.EMPTY);
        assertThat(response.points()).isEmpty();
        mockMvc.perform(get("/api/v1/portfolio/history")
                        .param("period", "14D")
                        .with(login(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void computes24HourChangeFromSnapshotPointsAndPropagatesStaleStatus() throws Exception {
        User owner = createUser("history-change-stale");
        Instant now = Instant.now().minusSeconds(2);
        Instant baselineAt = now.minusSeconds(25 * 3_600);
        Instant currentAt = now.minusSeconds(3_600);
        insertSnapshot(owner, baselineAt, "100", "COMPLETE");
        insertSnapshot(owner, currentAt, "150", "STALE");

        PortfolioSummaryResponse response = getSummary(owner);

        assertThat(response.summary().change24h().amountJpy()).isEqualByComparingTo("50");
        assertThat(response.summary().change24h().percentage()).isEqualByComparingTo("50.00000000");
        assertThat(response.summary().change24h().status().name()).isEqualTo("STALE");
        assertThat(response.summary().change24h().baselineSnapshotAt()).isEqualTo(baselineAt);
        assertThat(response.summary().change24h().currentSnapshotAt()).isEqualTo(currentAt);
    }

    @Test
    void returnsUnavailableWithoutAComparableSnapshotAndDoesNotDivideByZero() throws Exception {
        User noBaseline = createUser("history-change-unavailable");
        User zeroBaseline = createUser("history-change-zero");
        User negativeBaseline = createUser("history-change-negative");
        Instant now = Instant.now().minusSeconds(2);
        insertSnapshot(noBaseline, now.minusSeconds(3_600), "150", "COMPLETE");
        insertSnapshot(zeroBaseline, now.minusSeconds(25 * 3_600), "0", "COMPLETE");
        insertSnapshot(zeroBaseline, now.minusSeconds(3_600), "10", "COMPLETE");
        insertSnapshot(negativeBaseline, now.minusSeconds(25 * 3_600), "-10", "COMPLETE");
        insertSnapshot(negativeBaseline, now.minusSeconds(3_600), "-5", "COMPLETE");

        PortfolioSummaryResponse unavailable = getSummary(noBaseline);
        PortfolioSummaryResponse zero = getSummary(zeroBaseline);
        PortfolioSummaryResponse negative = getSummary(negativeBaseline);

        assertThat(unavailable.summary().change24h().amountJpy()).isNull();
        assertThat(unavailable.summary().change24h().percentage()).isNull();
        assertThat(unavailable.summary().change24h().status().name()).isEqualTo("UNAVAILABLE");
        assertThat(zero.summary().change24h().amountJpy()).isEqualByComparingTo("10");
        assertThat(zero.summary().change24h().percentage()).isNull();
        assertThat(negative.summary().change24h().amountJpy()).isEqualByComparingTo("5");
        assertThat(negative.summary().change24h().percentage()).isNull();
    }

    @Test
    void historyEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/history"))
                .andExpect(status().isUnauthorized());
    }

    private PortfolioHistoryResponse getHistory(User user, String period) throws Exception {
        String json = mockMvc.perform(get("/api/v1/portfolio/history")
                        .param("period", period)
                        .with(login(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, PortfolioHistoryResponse.class);
    }

    private PortfolioSummaryResponse getSummary(User user) throws Exception {
        String json = mockMvc.perform(get("/api/v1/portfolio/summary").with(login(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(json, PortfolioSummaryResponse.class);
    }

    private void insertSnapshot(User user, Instant at, String netWorth, String status) {
        BigDecimal value = new BigDecimal(netWorth);
        jdbcTemplate.update("""
                INSERT INTO portfolio_snapshots (
                    id, user_id, snapshot_at, data_as_of_at, net_worth_jpy,
                    holdings_value_jpy, directional_value_jpy, stablecoin_value_jpy,
                    market_exposure_jpy, unrealized_pnl_jpy, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), user.getId(), Timestamp.from(at), Timestamp.from(at),
                value, value, value, BigDecimal.ZERO, value, BigDecimal.ZERO, status);
    }

    private User createUser(String prefix) {
        return userRepository.saveAndFlush(new User(
                prefix + "-" + UUID.randomUUID(), prefix + "@example.test", "History Test User", null));
    }

    private RequestPostProcessor login(User user) {
        return oidcLogin().idToken(token -> token.subject(user.getGoogleSubject())
                .claim("email", user.getEmail()).claim("email_verified", true));
    }
}
