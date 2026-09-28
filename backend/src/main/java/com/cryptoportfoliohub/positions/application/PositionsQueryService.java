package com.cryptoportfoliohub.positions.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.domain.money.AssetQuantity;
import com.cryptoportfoliohub.domain.money.CurrencyCode;
import com.cryptoportfoliohub.domain.money.DisplayRounding;
import com.cryptoportfoliohub.domain.money.PerpetualValuation;
import com.cryptoportfoliohub.domain.money.Price;
import com.cryptoportfoliohub.marketdata.MarketDataService;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketFxQuote;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStatus;
import com.cryptoportfoliohub.persistence.entity.PerpetualPosition;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.PerpetualPositionRepository;
import com.cryptoportfoliohub.portfolio.application.PortfolioValuationService;
import com.cryptoportfoliohub.positions.api.PositionDataStatus;
import com.cryptoportfoliohub.positions.api.PositionsResponse;

@Service
public class PositionsQueryService {

    private static final int JPY_SCALE = 8;

    private final PortfolioValuationService portfolioValuationService;
    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final PerpetualPositionRepository positionRepository;
    private final MarketDataService marketDataService;
    private final PerpetualValuation perpetualValuation = new PerpetualValuation();

    public PositionsQueryService(
            PortfolioValuationService portfolioValuationService,
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository,
            PerpetualPositionRepository positionRepository,
            MarketDataService marketDataService) {
        this.portfolioValuationService = portfolioValuationService;
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
        this.positionRepository = positionRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public PositionsResponse getPositions(UUID authenticatedUserId) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        portfolioValuationService.valueUser(authenticatedUserId);

        List<ConnectionEntity> activeConnections = connectionRepository
                .findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(authenticatedUserId);
        List<ConnectionEntity> positionConnections = activeConnections.stream()
                .filter(connection -> supportsPositions(connection.getProvider()))
                .toList();
        Set<UUID> positionConnectionIds = positionConnections.stream()
                .map(ConnectionEntity::getId)
                .collect(Collectors.toSet());
        Map<UUID, ConnectionEntity> connectionById = positionConnections.stream()
                .collect(Collectors.toMap(ConnectionEntity::getId, connection -> connection));
        Map<UUID, ConnectionSyncState> positionSyncStates = positionSyncStates(authenticatedUserId);
        List<PerpetualPosition> positions = positionRepository
                .findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId).stream()
                .filter(position -> positionConnectionIds.contains(position.getConnection().getId()))
                .sorted(Comparator.comparing(PerpetualPosition::getFetchedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PerpetualPosition::getPositionKey))
                .toList();
        Map<String, MarketFxQuote> fxQuotes = fxQuotes(positions);

        List<ValuedPosition> valuedPositions = positions.stream()
                .map(position -> value(position, connectionById.get(position.getConnection().getId()),
                        positionSyncStates.get(position.getConnection().getId()), fxQuotes))
                .toList();
        boolean allConnectionsHavePositionHistory = positionConnections.stream()
                .allMatch(connection -> hasSuccessfulSync(positionSyncStates.get(connection.getId())));
        boolean anyConnectionHasPositionHistory = positionConnections.stream()
                .anyMatch(connection -> hasSuccessfulSync(positionSyncStates.get(connection.getId())));
        boolean allConnectionsCurrentlyReady = positionConnections.stream()
                .allMatch(connection -> isReady(positionSyncStates.get(connection.getId())));
        boolean positionDataScopeKnown = !activeConnections.isEmpty() && allConnectionsHavePositionHistory;

        Optional<BigDecimal> positionValueJpy = sum(valuedPositions.stream()
                .map(ValuedPosition::positionValueJpy).toList(), positionDataScopeKnown);
        Optional<BigDecimal> marginJpy = sum(valuedPositions.stream()
                .map(ValuedPosition::marginJpy).toList(), positionDataScopeKnown);
        Optional<BigDecimal> unrealizedPnlJpy = sum(valuedPositions.stream()
                .map(ValuedPosition::unrealizedPnlJpy).toList(), positionDataScopeKnown);
        PositionDataStatus summaryStatus = summaryStatus(
                activeConnections.isEmpty(),
                allConnectionsHavePositionHistory,
                anyConnectionHasPositionHistory,
                allConnectionsCurrentlyReady,
                valuedPositions,
                positionValueJpy.isPresent() && marginJpy.isPresent() && unrealizedPnlJpy.isPresent());

        return new PositionsResponse(
                new PositionsResponse.Summary(
                        positionValueJpy.orElse(null),
                        marginJpy.orElse(null),
                        unrealizedPnlJpy.orElse(null),
                        summaryStatus,
                        positionConnections.size(),
                        (int) positionConnections.stream()
                                .filter(connection -> hasSuccessfulSync(positionSyncStates.get(connection.getId())))
                                .count()),
                valuedPositions.stream().map(ValuedPosition::response).toList());
    }

    private Map<UUID, ConnectionSyncState> positionSyncStates(UUID authenticatedUserId) {
        Map<UUID, ConnectionSyncState> states = new HashMap<>();
        syncStateRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId).stream()
                .filter(state -> state.getId().getCapability() == SyncCapability.POSITION)
                .forEach(state -> states.put(state.getId().getConnectionId(), state));
        return states;
    }

    private Map<String, MarketFxQuote> fxQuotes(Collection<PerpetualPosition> positions) {
        Set<String> currencies = positions.stream()
                .flatMap(position -> java.util.stream.Stream.of(
                        position.getPriceCurrency(), position.getMarginCurrency(), effectivePnlCurrency(position)))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, MarketFxQuote> result = new HashMap<>();
        currencies.forEach(currency -> currencyCode(currency).ifPresent(code ->
                result.put(currency, marketDataService.fxRate(code, CurrencyCode.JPY))));
        return Map.copyOf(result);
    }

    private ValuedPosition value(
            PerpetualPosition position,
            ConnectionEntity connection,
            ConnectionSyncState syncState,
            Map<String, MarketFxQuote> fxQuotes) {
        String pnlCurrency = effectivePnlCurrency(position);
        Optional<BigDecimal> pnlAmount = unrealizedPnl(position);
        BigDecimal positionAmount = position.getMarkPrice() == null
                ? null
                : position.getQuantity().multiply(position.getMarkPrice()).abs();
        Optional<BigDecimal> positionJpy = isKnownSync(syncState)
                ? toJpy(positionAmount, position.getPriceCurrency(), position.getPriceFxRateToJpy())
                : Optional.empty();
        Optional<BigDecimal> marginJpy = isKnownSync(syncState)
                ? toJpy(position.getMarginAmount(), position.getMarginCurrency(), position.getMarginFxRateToJpy())
                : Optional.empty();
        Optional<BigDecimal> pnlJpy = isKnownSync(syncState)
                ? pnlAmount.flatMap(amount -> toJpy(amount, pnlCurrency, position.getPnlFxRateToJpy()))
                : Optional.empty();

        PositionsResponse.FxMetadata priceFx = fxMetadata(
                position.getPriceCurrency(), position.getPriceFxRateToJpy(), position.getPriceFxSource(),
                position.getPriceFxEvaluatedAt(), fxQuotes);
        PositionsResponse.FxMetadata marginFx = fxMetadata(
                position.getMarginCurrency(), position.getMarginFxRateToJpy(), position.getMarginFxSource(),
                position.getMarginFxEvaluatedAt(), fxQuotes);
        PositionsResponse.FxMetadata pnlFx = fxMetadata(
                pnlCurrency, position.getPnlFxRateToJpy(), position.getPnlFxSource(),
                position.getPnlFxEvaluatedAt(), fxQuotes);

        boolean anyValuePresent = positionJpy.isPresent() || marginJpy.isPresent() || pnlJpy.isPresent();
        boolean everyValuePresent = positionJpy.isPresent() && marginJpy.isPresent() && pnlJpy.isPresent();
        boolean staleFx = priceFx.status() == PositionDataStatus.STALE
                || marginFx.status() == PositionDataStatus.STALE
                || pnlFx.status() == PositionDataStatus.STALE;
        PositionDataStatus status;
        if (!hasSuccessfulSync(syncState)) {
            status = PositionDataStatus.UNAVAILABLE;
        } else if (!anyValuePresent) {
            status = PositionDataStatus.UNAVAILABLE;
        } else if (!everyValuePresent) {
            status = PositionDataStatus.PARTIAL;
        } else if (!isReady(syncState) || staleFx) {
            status = PositionDataStatus.STALE;
        } else {
            status = PositionDataStatus.COMPLETE;
        }

        return new ValuedPosition(
                new PositionsResponse.Position(
                        position.getPositionKey(),
                        position.getInstrumentCode(),
                        position.getSide(),
                        position.getLeverage(),
                        position.getQuantity(),
                        position.getEntryPrice(),
                        position.getMarkPrice(),
                        position.getLiquidationPrice(),
                        position.getPriceCurrency(),
                        positionJpy.orElse(null),
                        position.getMarginAmount(),
                        position.getMarginCurrency(),
                        marginJpy.orElse(null),
                        pnlAmount.orElse(null),
                        pnlCurrency,
                        pnlJpy.orElse(null),
                        priceFx,
                        marginFx,
                        pnlFx,
                        status,
                        connection.getId(),
                        connection.getProvider(),
                        connection.getDisplayName(),
                        position.getFetchedAt(),
                        syncState == null ? null : syncState.getLastSuccessAt()),
                positionJpy,
                marginJpy,
                pnlJpy,
                status);
    }

    private PositionsResponse.FxMetadata fxMetadata(
            String currency,
            BigDecimal recordedRate,
            String recordedSource,
            Instant recordedAt,
            Map<String, MarketFxQuote> fxQuotes) {
        if (currency == null) {
            return new PositionsResponse.FxMetadata(null, recordedRate, recordedSource, recordedAt,
                    PositionDataStatus.UNAVAILABLE);
        }
        MarketFxQuote quote = fxQuotes.get(currency);
        PositionDataStatus status = quote == null
                ? PositionDataStatus.UNAVAILABLE
                : freshnessStatus(quote.freshness());
        return new PositionsResponse.FxMetadata(currency, recordedRate, recordedSource, recordedAt, status);
    }

    private Optional<BigDecimal> toJpy(BigDecimal amount, String currency, BigDecimal fxRateToJpy) {
        if (amount == null) {
            return Optional.empty();
        }
        if (amount.signum() == 0) {
            return Optional.of(BigDecimal.ZERO.setScale(JPY_SCALE));
        }
        if (currency == null || fxRateToJpy == null) {
            return Optional.empty();
        }
        return Optional.of(DisplayRounding.round(
                amount.multiply(fxRateToJpy), JPY_SCALE, RoundingMode.HALF_UP));
    }

    private Optional<BigDecimal> unrealizedPnl(PerpetualPosition position) {
        if (position.getUnrealizedPnl() != null) {
            return Optional.of(position.getUnrealizedPnl());
        }
        if (position.getEntryPrice() == null || position.getMarkPrice() == null
                || position.getPriceCurrency() == null) {
            return Optional.empty();
        }
        Optional<CurrencyCode> currency = currencyCode(position.getPriceCurrency());
        if (currency.isEmpty()) {
            return Optional.empty();
        }
        try {
            Price entry = new Price(position.getInstrumentCode(), position.getEntryPrice(), currency.orElseThrow());
            Price mark = new Price(position.getInstrumentCode(), position.getMarkPrice(), currency.orElseThrow());
            return Optional.of(perpetualValuation.linearUnrealizedPnl(
                    position.getSide(), new AssetQuantity(position.getInstrumentCode(), position.getQuantity()),
                    entry, mark).amount());
        } catch (IllegalArgumentException invalidPrice) {
            return Optional.empty();
        }
    }

    private String effectivePnlCurrency(PerpetualPosition position) {
        if (position.getUnrealizedPnl() != null) {
            return position.getPnlCurrency();
        }
        return position.getEntryPrice() != null && position.getMarkPrice() != null
                ? position.getPriceCurrency() : null;
    }

    private Optional<CurrencyCode> currencyCode(String currency) {
        if (currency == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CurrencyCode(currency));
        } catch (IllegalArgumentException invalidCurrency) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> sum(List<Optional<BigDecimal>> values, boolean allConnectionsHaveHistory) {
        if (!allConnectionsHaveHistory || values.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(values.stream().map(Optional::orElseThrow).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private PositionDataStatus summaryStatus(
            boolean noActiveConnections,
            boolean allConnectionsHaveHistory,
            boolean anyConnectionHasHistory,
            boolean allConnectionsCurrentlyReady,
            List<ValuedPosition> positions,
            boolean allSummaryValuesAvailable) {
        if (noActiveConnections) {
            return PositionDataStatus.UNAVAILABLE;
        }
        if (!allConnectionsHaveHistory) {
            return anyConnectionHasHistory ? PositionDataStatus.PARTIAL : PositionDataStatus.UNAVAILABLE;
        }
        if (!allSummaryValuesAvailable) {
            boolean anySummaryValueAvailable = positions.stream().anyMatch(position ->
                    position.positionValueJpy().isPresent()
                            || position.marginJpy().isPresent()
                            || position.unrealizedPnlJpy().isPresent());
            return anySummaryValueAvailable ? PositionDataStatus.PARTIAL : PositionDataStatus.UNAVAILABLE;
        }
        boolean stale = !allConnectionsCurrentlyReady
                || positions.stream().anyMatch(position -> position.status() == PositionDataStatus.STALE);
        return stale ? PositionDataStatus.STALE : PositionDataStatus.COMPLETE;
    }

    private PositionDataStatus freshnessStatus(DataFreshness freshness) {
        return switch (freshness) {
            case FRESH -> PositionDataStatus.COMPLETE;
            case STALE -> PositionDataStatus.STALE;
            case UNAVAILABLE -> PositionDataStatus.UNAVAILABLE;
        };
    }

    private boolean supportsPositions(ConnectionProvider provider) {
        return provider == ConnectionProvider.HYPERLIQUID;
    }

    private boolean hasSuccessfulSync(ConnectionSyncState state) {
        return state != null && state.getLastSuccessAt() != null;
    }

    private boolean isKnownSync(ConnectionSyncState state) {
        return hasSuccessfulSync(state);
    }

    private boolean isReady(ConnectionSyncState state) {
        return state != null && state.getStatus() == ConnectionSyncStatus.READY
                && state.getLastSuccessAt() != null;
    }

    private record ValuedPosition(
            PositionsResponse.Position response,
            Optional<BigDecimal> positionValueJpy,
            Optional<BigDecimal> marginJpy,
            Optional<BigDecimal> unrealizedPnlJpy,
            PositionDataStatus status) {
    }
}
