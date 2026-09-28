package com.cryptoportfoliohub.portfolio.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
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
import com.cryptoportfoliohub.marketdata.AssetMarketMapping;
import com.cryptoportfoliohub.marketdata.MarketDataService;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketFxQuote;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceQuote;
import com.cryptoportfoliohub.persistence.entity.AssetBalance;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionProvider;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStatus;
import com.cryptoportfoliohub.persistence.entity.PerpetualPosition;
import com.cryptoportfoliohub.persistence.entity.ProviderAccountState;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.ValuationStatus;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.persistence.repository.PerpetualPositionRepository;
import com.cryptoportfoliohub.persistence.repository.ProviderAccountStateRepository;
import com.cryptoportfoliohub.portfolio.domain.PortfolioBalanceValue;
import com.cryptoportfoliohub.portfolio.domain.PortfolioCalculator;
import com.cryptoportfoliohub.portfolio.domain.PortfolioPositionValue;
import com.cryptoportfoliohub.portfolio.domain.PortfolioValuation;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioStatus;
import com.cryptoportfoliohub.portfolio.domain.ConnectionPortfolioValuation;
import com.cryptoportfoliohub.portfolio.domain.UserPortfolioValuation;

@Service
public class PortfolioValuationService {

    private static final int JPY_SCALE = 8;
    private static final int FX_SCALE = 12;

    private final ConnectionRepository connectionRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final AssetBalanceRepository balanceRepository;
    private final PerpetualPositionRepository positionRepository;
    private final ProviderAccountStateRepository accountStateRepository;
    private final MarketDataService marketDataService;
    private final PortfolioCalculator calculator = new PortfolioCalculator();
    private final PerpetualValuation perpetualValuation = new PerpetualValuation();

    public PortfolioValuationService(
            ConnectionRepository connectionRepository,
            ConnectionSyncStateRepository syncStateRepository,
            AssetBalanceRepository balanceRepository,
            PerpetualPositionRepository positionRepository,
            ProviderAccountStateRepository accountStateRepository,
            MarketDataService marketDataService) {
        this.connectionRepository = connectionRepository;
        this.syncStateRepository = syncStateRepository;
        this.balanceRepository = balanceRepository;
        this.positionRepository = positionRepository;
        this.accountStateRepository = accountStateRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public PortfolioValuation valueUser(UUID authenticatedUserId) {
        return valueUserInternal(authenticatedUserId, false).portfolio();
    }

    @Transactional
    public UserPortfolioValuation valueUserWithConnections(UUID authenticatedUserId) {
        return valueUserInternal(authenticatedUserId, true);
    }

    private UserPortfolioValuation valueUserInternal(
            UUID authenticatedUserId, boolean includeConnectionValuations) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");
        List<ConnectionEntity> connections = connectionRepository
                .findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(authenticatedUserId);
        if (connections.isEmpty()) {
            return new UserPortfolioValuation(
                    calculator.calculate(List.of(), List.of(), Optional.empty(), false, false, false),
                    Map.of());
        }

        List<AssetBalance> balances = balanceRepository
                .findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId);
        List<PerpetualPosition> positions = positionRepository
                .findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId);
        List<ProviderAccountState> accountStates = accountStateRepository
                .findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId);
        List<ConnectionSyncState> syncStates = syncStateRepository
                .findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId);

        Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> syncByConnection = groupSyncStates(syncStates);
        SyncAssessment syncAssessment = assessRequiredSync(connections, syncByConnection);

        Map<String, MarketPriceQuote> priceQuotes = resolvePrices(balances);
        Map<String, MarketFxQuote> fxQuotes = resolveFxQuotes(balances, positions, accountStates, priceQuotes);
        accountStates.forEach(state -> valueAccountState(state, fxQuotes));

        List<PortfolioBalanceValue> balanceValues = new ArrayList<>();
        List<ComputedBalance> computedBalances = new ArrayList<>();
        for (AssetBalance balance : balances) {
            AssetEvaluation evaluation = valueBalance(balance, priceQuotes, fxQuotes);
            PortfolioBalanceValue value = new PortfolioBalanceValue(
                    evaluation.category(), evaluation.valueJpy(), evaluation.stale());
            balanceValues.add(value);
            computedBalances.add(new ComputedBalance(balance, value));
        }

        List<ComputedPosition> computedPositions = new ArrayList<>();
        List<PortfolioPositionValue> positionValues = new ArrayList<>();
        for (PerpetualPosition position : positions) {
            ComputedPosition computed = valuePosition(position, fxQuotes);
            computedPositions.add(computed);
            positionValues.add(computed.portfolioValue());
        }

        Map<UUID, List<ProviderAccountState>> statesByConnection = accountStates.stream()
                .collect(Collectors.groupingBy(state -> state.getConnection().getId()));
        Map<UUID, List<ComputedPosition>> positionsByConnection = computedPositions.stream()
                .collect(Collectors.groupingBy(value -> value.entity().getConnection().getId()));
        boolean netWorthAdjustmentKnown = syncAssessment.available();
        boolean netWorthAdjustmentStale = false;
        boolean netWorthSnapshotAdjustmentStale = false;
        BigDecimal netWorthAdjustment = BigDecimal.ZERO;
        if (netWorthAdjustmentKnown) {
            for (ConnectionEntity connection : connections) {
                if (connection.getProvider() != ConnectionProvider.HYPERLIQUID) {
                    continue;
                }
                Optional<NetWorthAdjustment> adjustment = hyperliquidAdjustment(
                        connection.getId(),
                        statesByConnection.getOrDefault(connection.getId(), List.of()),
                        positionsByConnection.getOrDefault(connection.getId(), List.of()),
                        fxQuotes);
                if (adjustment.isEmpty()) {
                    netWorthAdjustmentKnown = false;
                    break;
                }
                netWorthAdjustment = netWorthAdjustment.add(adjustment.orElseThrow().amountJpy());
                netWorthAdjustmentStale |= adjustment.orElseThrow().stale();
                netWorthSnapshotAdjustmentStale |= adjustment.orElseThrow().snapshotStale();
            }
        }

        Optional<Instant> dataAsOfAt = dataAsOfAt(
                connections, syncByConnection, balances, positions, accountStates);
        PortfolioValuation userValuation = calculator.calculate(
                balanceValues,
                positionValues,
                netWorthAdjustmentKnown ? Optional.of(roundJpy(netWorthAdjustment)) : Optional.empty(),
                !connections.isEmpty(),
                syncAssessment.available(),
                syncAssessment.stale() || netWorthAdjustmentStale,
                dataAsOfAt,
                syncAssessment.stale() || netWorthSnapshotAdjustmentStale);
        Map<UUID, ConnectionPortfolioValuation> connectionValuations = includeConnectionValuations
                ? connectionValuations(
                        connections,
                        syncByConnection,
                        computedBalances,
                        statesByConnection,
                        positionsByConnection,
                        fxQuotes)
                : Map.of();
        return new UserPortfolioValuation(userValuation, connectionValuations);
    }

    private Map<String, MarketPriceQuote> resolvePrices(List<AssetBalance> balances) {
        Set<String> canonicalKeys = balances.stream()
                .map(this::marketIdentity)
                .flatMap(Optional::stream)
                .map(AssetMarketMapping.Identity::canonicalAssetKey)
                .collect(Collectors.toSet());
        return canonicalKeys.isEmpty() ? Map.of() : marketDataService.currentPrices(canonicalKeys);
    }

    private Map<String, MarketFxQuote> resolveFxQuotes(
            List<AssetBalance> balances,
            List<PerpetualPosition> positions,
            List<ProviderAccountState> accountStates,
            Map<String, MarketPriceQuote> prices) {
        Set<String> currencies = new java.util.HashSet<>();
        balances.stream().map(this::marketIdentity).flatMap(Optional::stream)
                .map(identity -> prices.get(identity.canonicalAssetKey()))
                .filter(Objects::nonNull)
                .map(MarketPriceQuote::price).flatMap(Optional::stream)
                .map(price -> price.currency().value()).forEach(currencies::add);
        positions.stream().flatMap(position -> java.util.stream.Stream.of(
                        position.getPriceCurrency(),
                        position.getMarginCurrency(),
                        effectivePnlCurrency(position)))
                .filter(Objects::nonNull).forEach(currencies::add);
        accountStates.stream().map(ProviderAccountState::getAccountCurrency)
                .filter(Objects::nonNull).forEach(currencies::add);
        return currencies.stream().distinct().collect(Collectors.toMap(
                currency -> currency,
                currency -> currencyCode(currency)
                        .map(code -> marketDataService.fxRate(code, CurrencyCode.JPY))
                        .orElseGet(() -> unavailableFx(currency)),
                (left, right) -> left,
                HashMap::new));
    }

    private AssetEvaluation valueBalance(
            AssetBalance balance,
            Map<String, MarketPriceQuote> prices,
            Map<String, MarketFxQuote> fxQuotes) {
        Optional<AssetMarketMapping.Identity> identity = marketIdentity(balance);
        AssetCategory category = identity.map(AssetMarketMapping.Identity::category)
                .orElse(balance.getAssetCategory());
        if (balance.getTotalQuantity().signum() == 0) {
            balance.recordValuation(category, null, null, null, null,
                    null, null, null, BigDecimal.ZERO.setScale(JPY_SCALE), ValuationStatus.VALUED);
            return new AssetEvaluation(category, Optional.of(BigDecimal.ZERO.setScale(JPY_SCALE)), false);
        }

        if (balance.getAssetCategory() == AssetCategory.FIAT
                && "BITBANK".equals(balance.getNetwork())
                && "JPY".equals(balance.getAssetKey())) {
            BigDecimal value = roundJpy(balance.getTotalQuantity());
            balance.recordValuation(AssetCategory.FIAT, BigDecimal.ONE, "JPY", "IDENTITY", null,
                    BigDecimal.ONE, "IDENTITY", null, value, ValuationStatus.VALUED);
            return new AssetEvaluation(AssetCategory.FIAT, Optional.of(value), false);
        }

        if (identity.isEmpty()) {
            balance.recordValuation(category, null, null, null, null,
                    null, null, null, null, ValuationStatus.UNAVAILABLE);
            return new AssetEvaluation(category, Optional.empty(), false);
        }
        MarketPriceQuote priceQuote = prices.get(identity.orElseThrow().canonicalAssetKey());
        if (priceQuote == null || priceQuote.price().isEmpty()) {
            balance.recordValuation(category, null, null,
                    priceQuote == null ? null : sourceName(priceQuote.source()),
                    priceQuote == null ? null : priceQuote.evaluatedAt().orElse(null),
                    null, null, null, null, ValuationStatus.UNAVAILABLE);
            return new AssetEvaluation(category, Optional.empty(), false);
        }
        var price = priceQuote.price().orElseThrow();
        MarketFxQuote fxQuote = fxQuotes.get(price.currency().value());
        if (fxQuote == null || fxQuote.rate().isEmpty()) {
            balance.recordValuation(category, price.amount(), price.currency().value(),
                    sourceName(priceQuote.source()), priceQuote.evaluatedAt().orElse(null),
                    null, sourceName(fxQuote == null ? Optional.empty() : fxQuote.source()),
                    fxQuote == null ? null : fxQuote.evaluatedAt().orElse(null), null, ValuationStatus.UNAVAILABLE);
            return new AssetEvaluation(category, Optional.empty(), priceQuote.freshness() == DataFreshness.STALE);
        }
        var rate = normalizeFxRate(fxQuote.rate().orElseThrow().rate());
        BigDecimal value = roundJpy(balance.getTotalQuantity().multiply(price.amount()).multiply(rate));
        balance.recordValuation(category, price.amount(), price.currency().value(),
                sourceName(priceQuote.source()), priceQuote.evaluatedAt().orElse(null),
                rate, sourceName(fxQuote.source()), fxQuote.evaluatedAt().orElse(null),
                value, ValuationStatus.VALUED);
        return new AssetEvaluation(category, Optional.of(value),
                priceQuote.freshness() == DataFreshness.STALE || fxQuote.freshness() == DataFreshness.STALE);
    }

    private ComputedPosition valuePosition(PerpetualPosition position, Map<String, MarketFxQuote> fxQuotes) {
        String effectivePnlCurrency = effectivePnlCurrency(position);
        Optional<MarketFxQuote> priceFx = fxQuote(position.getPriceCurrency(), fxQuotes);
        Optional<MarketFxQuote> marginFx = fxQuote(position.getMarginCurrency(), fxQuotes);
        Optional<MarketFxQuote> pnlFx = fxQuote(effectivePnlCurrency, fxQuotes);
        position.recordFxValuations(
                rate(priceFx), sourceName(priceFx.flatMap(MarketFxQuote::source)), evaluatedAt(priceFx),
                rate(marginFx), sourceName(marginFx.flatMap(MarketFxQuote::source)), evaluatedAt(marginFx),
                rate(pnlFx), sourceName(pnlFx.flatMap(MarketFxQuote::source)), evaluatedAt(pnlFx));

        Optional<BigDecimal> positionJpy = Optional.ofNullable(position.getMarkPrice())
                .flatMap(mark -> toJpy(position.getQuantity().multiply(mark).abs(), position.getPriceCurrency(), priceFx));
        Optional<BigDecimal> marginJpy = Optional.ofNullable(position.getMarginAmount())
                .flatMap(amount -> toJpy(amount, position.getMarginCurrency(), marginFx));
        Optional<BigDecimal> pnlAmount = unrealizedPnl(position);
        Optional<BigDecimal> pnlJpy = pnlAmount.flatMap(amount -> toJpy(amount, effectivePnlCurrency, pnlFx));
        boolean snapshotStale = isStale(priceFx) || isStale(pnlFx);
        boolean stale = snapshotStale || isStale(marginFx);
        return new ComputedPosition(position,
                new PortfolioPositionValue(positionJpy, marginJpy, pnlJpy, stale, snapshotStale,
                        isStale(pnlFx)),
                pnlJpy,
                positionScope(position));
    }

    private void valueAccountState(ProviderAccountState state, Map<String, MarketFxQuote> fxQuotes) {
        if (state.getAccountEquity() == null) {
            state.recordFxValuation(null, null, null, null);
            return;
        }
        Optional<MarketFxQuote> accountFx = fxQuote(state.getAccountCurrency(), fxQuotes);
        Optional<BigDecimal> equityJpy = toJpy(
                state.getAccountEquity(), state.getAccountCurrency(), accountFx);
        state.recordFxValuation(
                rate(accountFx), sourceName(accountFx.flatMap(MarketFxQuote::source)), evaluatedAt(accountFx),
                equityJpy.orElse(null));
    }

    private Optional<NetWorthAdjustment> hyperliquidAdjustment(
            UUID connectionId,
            List<ProviderAccountState> states,
            List<ComputedPosition> positions,
            Map<String, MarketFxQuote> fxQuotes) {
        Optional<ProviderAccountState> account = states.stream()
                .filter(state -> "ACCOUNT".equals(state.getAccountScope())).findFirst();
        if (account.isEmpty()) {
            return Optional.empty();
        }
        String mode = account.orElseThrow().getAccountMode();
        if (mode == null || !Set.of("STANDARD", "UNIFIED_ACCOUNT", "PORTFOLIO_MARGIN").contains(mode)) {
            return Optional.empty();
        }
        if (states.stream().anyMatch(state -> !mode.equals(state.getAccountMode()))) {
            return Optional.empty();
        }
        if ("STANDARD".equals(mode)) {
            List<ProviderAccountState> dexStates = states.stream()
                    .filter(state -> state.getAccountScope().startsWith("PERP_DEX:"))
                    .toList();
            if (dexStates.isEmpty()) {
                return Optional.empty();
            }
            BigDecimal total = BigDecimal.ZERO;
            boolean stale = false;
            for (ProviderAccountState state : dexStates) {
                Optional<MarketFxQuote> accountFx = fxQuote(state.getAccountCurrency(), fxQuotes);
                Optional<BigDecimal> equityJpy = Optional.ofNullable(state.getAccountEquityJpy());
                if (equityJpy.isEmpty() || state.getEquityIncludesUnrealizedPnl() == null) {
                    return Optional.empty();
                }
                stale |= isStale(accountFx);
                total = total.add(equityJpy.orElseThrow());
                if (!state.getEquityIncludesUnrealizedPnl()) {
                    List<ComputedPosition> scopePositions = positions.stream()
                            .filter(position -> state.getAccountScope().equals(position.scope())).toList();
                    if (positions.stream().anyMatch(position -> position.scope() == null)) {
                        return Optional.empty();
                    }
                    for (ComputedPosition position : scopePositions) {
                        if (position.unrealizedPnlJpy().isEmpty()) {
                            return Optional.empty();
                        }
                        total = total.add(position.unrealizedPnlJpy().orElseThrow());
                        stale |= position.portfolioValue().pnlStale();
                    }
                }
            }
            return Optional.of(new NetWorthAdjustment(roundJpy(total), stale, stale));
        }
        BigDecimal totalPnl = BigDecimal.ZERO;
        for (ComputedPosition position : positions) {
            if (position.unrealizedPnlJpy().isEmpty()) {
                return Optional.empty();
            }
            totalPnl = totalPnl.add(position.unrealizedPnlJpy().orElseThrow());
        }
        return Optional.of(new NetWorthAdjustment(roundJpy(totalPnl),
                positions.stream().anyMatch(position -> position.portfolioValue().pnlStale()),
                positions.stream().anyMatch(position -> position.portfolioValue().snapshotStale())));
    }

    private Map<UUID, ConnectionPortfolioValuation> connectionValuations(
            List<ConnectionEntity> connections,
            Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> syncByConnection,
            List<ComputedBalance> balances,
            Map<UUID, List<ProviderAccountState>> statesByConnection,
            Map<UUID, List<ComputedPosition>> positionsByConnection,
            Map<String, MarketFxQuote> fxQuotes) {
        Map<UUID, ConnectionPortfolioValuation> result = new HashMap<>();
        for (ConnectionEntity connection : connections) {
            List<ComputedBalance> scopedBalances = balances.stream()
                    .filter(value -> connection.getId().equals(value.entity().getConnection().getId()))
                    .toList();
            List<ProviderAccountState> scopedStates = statesByConnection.getOrDefault(
                    connection.getId(), List.of());
            List<ComputedPosition> scopedPositions = positionsByConnection.getOrDefault(
                    connection.getId(), List.of());
            SyncAssessment sync = assessRequiredSync(List.of(connection), syncByConnection);

            boolean balancesComplete = scopedBalances.stream()
                    .allMatch(value -> value.value().valueJpy().isPresent());
            Optional<BigDecimal> holdings = balancesComplete
                    ? Optional.of(scopedBalances.stream()
                            .map(value -> value.value().valueJpy().orElseThrow())
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    : Optional.empty();
            Optional<NetWorthAdjustment> adjustment = connection.getProvider() == ConnectionProvider.HYPERLIQUID
                    ? hyperliquidAdjustment(connection.getId(), scopedStates, scopedPositions, fxQuotes)
                    : Optional.of(new NetWorthAdjustment(BigDecimal.ZERO, false, false));

            if (sync.available() && holdings.isPresent() && adjustment.isPresent()) {
                boolean stale = sync.stale()
                        || scopedBalances.stream().anyMatch(value -> value.value().stale())
                        || adjustment.orElseThrow().stale();
                result.put(connection.getId(), new ConnectionPortfolioValuation(
                        Optional.of(roundJpy(holdings.orElseThrow()
                                .add(adjustment.orElseThrow().amountJpy()))),
                        stale ? ConnectionPortfolioStatus.STALE : ConnectionPortfolioStatus.COMPLETE));
                continue;
            }

            boolean hasCurrentState = !scopedBalances.isEmpty()
                    || !scopedPositions.isEmpty()
                    || !scopedStates.isEmpty();
            result.put(connection.getId(), new ConnectionPortfolioValuation(
                    Optional.empty(),
                    hasCurrentState || sync.available()
                            ? ConnectionPortfolioStatus.PARTIAL
                            : ConnectionPortfolioStatus.UNAVAILABLE));
        }
        return Map.copyOf(result);
    }

    private Optional<Instant> dataAsOfAt(
            List<ConnectionEntity> connections,
            Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> syncByConnection,
            List<AssetBalance> balances,
            List<PerpetualPosition> positions,
            List<ProviderAccountState> accountStates) {
        List<Instant> times = new ArrayList<>();
        for (ConnectionEntity connection : connections) {
            Set<SyncCapability> required = connection.getProvider() == ConnectionProvider.HYPERLIQUID
                    ? Set.of(SyncCapability.BALANCE, SyncCapability.POSITION, SyncCapability.ACCOUNT)
                    : Set.of(SyncCapability.BALANCE);
            for (SyncCapability capability : required) {
                ConnectionSyncState state = syncByConnection.getOrDefault(
                        connection.getId(), new EnumMap<>(SyncCapability.class)).get(capability);
                if (state == null || state.getLastSuccessAt() == null) {
                    return Optional.empty();
                }
                times.add(state.getLastSuccessAt());
            }
        }

        for (AssetBalance balance : balances) {
            if (balance.getFetchedAt() == null) {
                return Optional.empty();
            }
            times.add(balance.getFetchedAt());
            if (balance.getTotalQuantity().signum() == 0
                    || ("BITBANK".equals(balance.getNetwork()) && "JPY".equals(balance.getAssetKey()))) {
                continue;
            }
            if (balance.getJpyValue() == null || balance.getPriceEvaluatedAt() == null
                    || balance.getFxRateToJpy() == null) {
                return Optional.empty();
            }
            times.add(balance.getPriceEvaluatedAt());
            if (!"IDENTITY".equals(balance.getFxSource())) {
                if (balance.getFxEvaluatedAt() == null) {
                    return Optional.empty();
                }
                times.add(balance.getFxEvaluatedAt());
            } else if (balance.getFxEvaluatedAt() != null) {
                times.add(balance.getFxEvaluatedAt());
            }
        }

        for (PerpetualPosition position : positions) {
            if (position.getFetchedAt() == null) {
                return Optional.empty();
            }
            times.add(position.getFetchedAt());
            if (position.getQuantity().signum() != 0) {
                if (position.getMarkPrice() == null || position.getPriceFxRateToJpy() == null) {
                    return Optional.empty();
                }
                if (!"IDENTITY".equals(position.getPriceFxSource())) {
                    if (position.getPriceFxEvaluatedAt() == null) {
                        return Optional.empty();
                    }
                    times.add(position.getPriceFxEvaluatedAt());
                } else if (position.getPriceFxEvaluatedAt() != null) {
                    times.add(position.getPriceFxEvaluatedAt());
                }
            }
            Optional<BigDecimal> pnl = unrealizedPnl(position);
            if (pnl.isEmpty()) {
                return Optional.empty();
            }
            if (pnl.orElseThrow().signum() != 0) {
                String currency = effectivePnlCurrency(position);
                if (currency == null || position.getPnlFxRateToJpy() == null) {
                    return Optional.empty();
                }
                if (!"IDENTITY".equals(position.getPnlFxSource())) {
                    if (position.getPnlFxEvaluatedAt() == null) {
                        return Optional.empty();
                    }
                    times.add(position.getPnlFxEvaluatedAt());
                } else if (position.getPnlFxEvaluatedAt() != null) {
                    times.add(position.getPnlFxEvaluatedAt());
                }
            }
        }

        for (ProviderAccountState state : accountStates) {
            if (state.getFetchedAt() == null) {
                return Optional.empty();
            }
            times.add(state.getFetchedAt());
            if ("STANDARD".equals(state.getAccountMode())
                    && state.getAccountScope().startsWith("PERP_DEX:")
                    && state.getAccountEquity() != null
                    && state.getAccountEquity().signum() != 0) {
                if (state.getAccountEquityJpy() == null || state.getFxRateToJpy() == null) {
                    return Optional.empty();
                }
                if (!"IDENTITY".equals(state.getFxSource())) {
                    if (state.getFxEvaluatedAt() == null) {
                        return Optional.empty();
                    }
                    times.add(state.getFxEvaluatedAt());
                } else if (state.getFxEvaluatedAt() != null) {
                    times.add(state.getFxEvaluatedAt());
                }
            }
        }
        return times.stream().min(Instant::compareTo);
    }

    private SyncAssessment assessRequiredSync(
            List<ConnectionEntity> connections,
            Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> states) {
        boolean stale = false;
        for (ConnectionEntity connection : connections) {
            Set<SyncCapability> required = connection.getProvider() == ConnectionProvider.HYPERLIQUID
                    ? Set.of(SyncCapability.BALANCE, SyncCapability.POSITION, SyncCapability.ACCOUNT)
                    : Set.of(SyncCapability.BALANCE);
            for (SyncCapability capability : required) {
                ConnectionSyncState state = states.getOrDefault(connection.getId(), new EnumMap<>(SyncCapability.class))
                        .get(capability);
                if (state == null || state.getLastSuccessAt() == null) {
                    return new SyncAssessment(false, stale);
                }
                if (state.getStatus() != ConnectionSyncStatus.READY) {
                    stale = true;
                }
            }
        }
        return new SyncAssessment(true, stale);
    }

    private Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> groupSyncStates(List<ConnectionSyncState> syncStates) {
        Map<UUID, EnumMap<SyncCapability, ConnectionSyncState>> grouped = new HashMap<>();
        for (ConnectionSyncState state : syncStates) {
            grouped.computeIfAbsent(state.getId().getConnectionId(), ignored -> new EnumMap<>(SyncCapability.class))
                    .put(state.getId().getCapability(), state);
        }
        return grouped;
    }

    private Optional<AssetMarketMapping.Identity> marketIdentity(AssetBalance balance) {
        return AssetMarketMapping.resolve(balance.getAssetKey(), balance.getNetwork(), balance.getAssetRef());
    }

    private Optional<MarketFxQuote> fxQuote(String currency, Map<String, MarketFxQuote> fxQuotes) {
        return currency == null ? Optional.empty() : Optional.ofNullable(fxQuotes.get(currency));
    }

    private Optional<BigDecimal> toJpy(BigDecimal amount, String currency, Optional<MarketFxQuote> quote) {
        if (amount == null) {
            return Optional.empty();
        }
        if (amount.signum() == 0) {
            return Optional.of(BigDecimal.ZERO.setScale(JPY_SCALE));
        }
        if (currency == null || quote.isEmpty() || quote.orElseThrow().rate().isEmpty()) {
            return Optional.empty();
        }
        BigDecimal fxRate = normalizeFxRate(quote.orElseThrow().rate().orElseThrow().rate());
        return Optional.of(roundJpy(amount.multiply(fxRate)));
    }

    private Optional<BigDecimal> toJpy(BigDecimal amount, String currency, MarketFxQuote quote) {
        return toJpy(amount, currency, Optional.ofNullable(quote));
    }

    private Optional<BigDecimal> unrealizedPnl(PerpetualPosition position) {
        if (position.getUnrealizedPnl() != null) {
            return Optional.of(position.getUnrealizedPnl());
        }
        if (position.getEntryPrice() == null || position.getMarkPrice() == null
                || position.getPriceCurrency() == null) {
            return Optional.empty();
        }
        try {
            CurrencyCode currency = new CurrencyCode(position.getPriceCurrency());
            Price entry = new Price(position.getInstrumentCode(), position.getEntryPrice(), currency);
            Price mark = new Price(position.getInstrumentCode(), position.getMarkPrice(), currency);
            return Optional.of(perpetualValuation.linearUnrealizedPnl(
                    position.getSide(), new AssetQuantity(position.getInstrumentCode(), position.getQuantity()), entry, mark).amount());
        } catch (IllegalArgumentException invalidCurrencyOrPrice) {
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

    private String positionScope(PerpetualPosition position) {
        if (position.getPositionKey().startsWith("DEFAULT:")) {
            return "PERP_DEX:DEFAULT";
        }
        int separator = position.getInstrumentCode().indexOf(':');
        if (separator > 0) {
            return "PERP_DEX:" + position.getInstrumentCode().substring(0, separator);
        }
        return null;
    }

    private Optional<CurrencyCode> currencyCode(String currency) {
        try {
            return Optional.of(new CurrencyCode(currency));
        } catch (IllegalArgumentException invalidCurrency) {
            return Optional.empty();
        }
    }

    private MarketFxQuote unavailableFx(String currency) {
        CurrencyCode from = currencyCode(currency).orElse(CurrencyCode.USD);
        return new MarketFxQuote(from, CurrencyCode.JPY, Optional.empty(), Optional.empty(), Optional.empty(),
                DataFreshness.UNAVAILABLE, Optional.empty());
    }

    private BigDecimal rate(Optional<MarketFxQuote> quote) {
        return quote.flatMap(MarketFxQuote::rate).map(rate -> normalizeFxRate(rate.rate())).orElse(null);
    }

    private String sourceName(Optional<com.cryptoportfoliohub.marketdata.domain.MarketDataSource> source) {
        return source.map(Enum::name).orElse(null);
    }

    private Instant evaluatedAt(Optional<MarketFxQuote> quote) {
        return quote.flatMap(MarketFxQuote::evaluatedAt).orElse(null);
    }

    private boolean isStale(Optional<MarketFxQuote> quote) {
        return quote.map(value -> value.freshness() == DataFreshness.STALE).orElse(false);
    }

    private static BigDecimal roundJpy(BigDecimal amount) {
        return DisplayRounding.round(amount, JPY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeFxRate(BigDecimal rate) {
        return DisplayRounding.round(rate, FX_SCALE, RoundingMode.HALF_UP);
    }

    private record AssetEvaluation(AssetCategory category, Optional<BigDecimal> valueJpy, boolean stale) {
    }

    private record ComputedPosition(
            PerpetualPosition entity,
            PortfolioPositionValue portfolioValue,
            Optional<BigDecimal> unrealizedPnlJpy,
            String scope) {
    }

    private record ComputedBalance(AssetBalance entity, PortfolioBalanceValue value) {
    }

    private record NetWorthAdjustment(BigDecimal amountJpy, boolean stale, boolean snapshotStale) {
    }

    private record SyncAssessment(boolean available, boolean stale) {
    }
}
