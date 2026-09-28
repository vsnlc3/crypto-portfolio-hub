package com.cryptoportfoliohub.assets.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoportfoliohub.assets.api.AssetDataStatus;
import com.cryptoportfoliohub.assets.api.AssetsResponse;
import com.cryptoportfoliohub.domain.money.CurrencyCode;
import com.cryptoportfoliohub.marketdata.AssetMarketMapping;
import com.cryptoportfoliohub.marketdata.MarketDataService;
import com.cryptoportfoliohub.marketdata.domain.DataFreshness;
import com.cryptoportfoliohub.marketdata.domain.MarketFxQuote;
import com.cryptoportfoliohub.marketdata.domain.MarketPriceQuote;
import com.cryptoportfoliohub.persistence.entity.AssetBalance;
import com.cryptoportfoliohub.persistence.entity.AssetCategory;
import com.cryptoportfoliohub.persistence.entity.ConnectionEntity;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncState;
import com.cryptoportfoliohub.persistence.entity.ConnectionSyncStatus;
import com.cryptoportfoliohub.persistence.entity.SyncCapability;
import com.cryptoportfoliohub.persistence.entity.ValuationStatus;
import com.cryptoportfoliohub.persistence.repository.AssetBalanceRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionRepository;
import com.cryptoportfoliohub.persistence.repository.ConnectionSyncStateRepository;
import com.cryptoportfoliohub.portfolio.application.PortfolioValuationService;

@Service
public class AssetsQueryService {

    private final PortfolioValuationService portfolioValuationService;
    private final ConnectionRepository connectionRepository;
    private final AssetBalanceRepository balanceRepository;
    private final ConnectionSyncStateRepository syncStateRepository;
    private final MarketDataService marketDataService;

    public AssetsQueryService(
            PortfolioValuationService portfolioValuationService,
            ConnectionRepository connectionRepository,
            AssetBalanceRepository balanceRepository,
            ConnectionSyncStateRepository syncStateRepository,
            MarketDataService marketDataService) {
        this.portfolioValuationService = portfolioValuationService;
        this.connectionRepository = connectionRepository;
        this.balanceRepository = balanceRepository;
        this.syncStateRepository = syncStateRepository;
        this.marketDataService = marketDataService;
    }

    @Transactional
    public AssetsResponse getAssets(UUID authenticatedUserId) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId must not be null");

        // Refreshes persisted Balance valuation inputs using the same owner-scoped rules as Dashboard.
        portfolioValuationService.valueUser(authenticatedUserId);

        List<ConnectionEntity> connections = connectionRepository
                .findAllByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(authenticatedUserId);
        List<AssetBalance> balances = balanceRepository
                .findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId);
        Map<UUID, ConnectionSyncState> balanceSync = balanceSyncStates(authenticatedUserId);
        boolean allConnectionsHaveBalanceHistory = !connections.isEmpty() && connections.stream()
                .allMatch(connection -> hasSuccessfulBalanceSync(balanceSync.get(connection.getId())));
        boolean anyConnectionHasBalanceHistory = connections.stream()
                .anyMatch(connection -> hasSuccessfulBalanceSync(balanceSync.get(connection.getId())));
        boolean anyConnectionHasCurrentBalanceSync = connections.stream()
                .allMatch(connection -> isReady(balanceSync.get(connection.getId())));

        Map<AssetIdentity, List<AssetBalance>> balancesByAsset = balances.stream()
                .collect(Collectors.groupingBy(this::identity, LinkedHashMap::new, Collectors.toList()));
        Set<String> marketKeys = balancesByAsset.keySet().stream()
                .map(AssetIdentity::marketKey)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        Map<String, MarketPriceQuote> quotes = marketKeys.isEmpty()
                ? Map.of()
                : marketDataService.currentPrices(marketKeys);
        Map<String, DataFreshness> fxFreshness = fxFreshnessByCurrency(balances);

        List<AssetBuild> builtAssets = balancesByAsset.entrySet().stream()
                .map(entry -> buildAsset(
                        entry.getKey(), entry.getValue(), connections, balanceSync, quotes, fxFreshness,
                        allConnectionsHaveBalanceHistory))
                .sorted(Comparator
                        .comparing((AssetBuild asset) -> asset.response().valueJpy(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(asset -> asset.response().symbol(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(asset -> asset.response().assetId()))
                .toList();

        boolean everyValuationAvailable = builtAssets.stream().allMatch(asset -> asset.response().valueJpy() != null);
        boolean anyValuationAvailable = builtAssets.stream().anyMatch(asset -> asset.response().valueJpy() != null);
        boolean staleValuation = builtAssets.stream().anyMatch(asset -> asset.stale());
        AssetDataStatus summaryStatus = summaryStatus(
                connections,
                allConnectionsHaveBalanceHistory,
                anyConnectionHasBalanceHistory,
                anyConnectionHasCurrentBalanceSync,
                everyValuationAvailable,
                anyValuationAvailable,
                staleValuation);

        BigDecimal spotHoldings = allConnectionsHaveBalanceHistory
                ? sumCategory(builtAssets, null)
                : null;
        BigDecimal directionalAssets = allConnectionsHaveBalanceHistory
                ? sumCategory(builtAssets, AssetCategory.CRYPTO)
                : null;
        BigDecimal stablecoins = allConnectionsHaveBalanceHistory
                ? sumCategory(builtAssets, AssetCategory.STABLECOIN)
                : null;
        Instant dataAsOfAt = balances.stream()
                .map(AssetBalance::getFetchedAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);

        return new AssetsResponse(
                new AssetsResponse.Summary(
                        spotHoldings,
                        directionalAssets,
                        stablecoins,
                        summaryStatus,
                        connections.size(),
                        (int) connections.stream()
                                .filter(connection -> hasSuccessfulBalanceSync(balanceSync.get(connection.getId()))).count(),
                        dataAsOfAt),
                builtAssets.stream().map(AssetBuild::response).toList());
    }

    private Map<UUID, ConnectionSyncState> balanceSyncStates(UUID authenticatedUserId) {
        Map<UUID, ConnectionSyncState> states = new HashMap<>();
        syncStateRepository.findAllByConnection_User_IdAndConnection_DeletedAtIsNull(authenticatedUserId).stream()
                .filter(state -> state.getId().getCapability() == SyncCapability.BALANCE)
                .forEach(state -> states.put(state.getId().getConnectionId(), state));
        return states;
    }

    private Map<String, DataFreshness> fxFreshnessByCurrency(List<AssetBalance> balances) {
        Set<String> currencies = balances.stream()
                .filter(balance -> balance.getTotalQuantity().signum() != 0)
                .map(AssetBalance::getPriceCurrency)
                .filter(Objects::nonNull)
                .filter(currency -> !"JPY".equals(currency))
                .collect(Collectors.toSet());
        Map<String, DataFreshness> result = new HashMap<>();
        currencies.forEach(currency -> {
            try {
                MarketFxQuote quote = marketDataService.fxRate(new CurrencyCode(currency), CurrencyCode.JPY);
                result.put(currency, quote.freshness());
            } catch (IllegalArgumentException ignored) {
                result.put(currency, DataFreshness.UNAVAILABLE);
            }
        });
        return result;
    }

    private AssetBuild buildAsset(
            AssetIdentity identity,
            List<AssetBalance> balances,
            List<ConnectionEntity> allConnections,
            Map<UUID, ConnectionSyncState> balanceSync,
            Map<String, MarketPriceQuote> quotes,
            Map<String, DataFreshness> fxFreshness,
            boolean allConnectionsHaveBalanceHistory) {
        List<AssetBalance> sortedBalances = balances.stream()
                .sorted(Comparator.comparing(balance -> balance.getConnection().getId()))
                .toList();
        BigDecimal quantity = sortedBalances.stream()
                .map(AssetBalance::getTotalQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean everyBalanceValued = sortedBalances.stream()
                .allMatch(balance -> balance.getValuationStatus() == ValuationStatus.VALUED
                        && balance.getJpyValue() != null);
        BigDecimal knownJpyValue = everyBalanceValued
                ? sortedBalances.stream().map(AssetBalance::getJpyValue).reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        MarketPriceQuote quote = identity.marketKey().map(quotes::get).orElse(null);
        boolean stale = sortedBalances.stream().anyMatch(balance -> stale(balance, balanceSync, quote, fxFreshness));
        boolean anyBalanceValued = sortedBalances.stream()
                .anyMatch(balance -> balance.getValuationStatus() == ValuationStatus.VALUED
                        && balance.getJpyValue() != null);
        AssetDataStatus status = aggregateStatus(
                everyBalanceValued && allConnectionsHaveBalanceHistory,
                anyBalanceValued,
                stale,
                sortedBalances.size());
        // If a Connection has never completed BALANCE sync, the cross-Connection asset amount is incomplete.
        BigDecimal aggregateQuantity = allConnectionsHaveBalanceHistory ? quantity : null;
        BigDecimal aggregateJpyValue = allConnectionsHaveBalanceHistory ? knownJpyValue : null;

        List<AssetsResponse.ConnectionHolding> holdings = connectionHoldings(
                sortedBalances, allConnections, balanceSync, quote, fxFreshness);
        return new AssetBuild(
                new AssetsResponse.Asset(
                        identity.assetId(),
                        identity.assetKey(),
                        identity.symbol(),
                        displayName(identity, sortedBalances),
                        identity.category(),
                        commonValue(sortedBalances.stream().map(AssetBalance::getNetwork).toList()),
                        aggregateQuantity,
                        aggregateJpyValue,
                        status,
                        priceResponse(quote, sortedBalances),
                        changeResponse(quote),
                        valuation(sortedBalances),
                        holdings),
                stale);
    }

    private List<AssetsResponse.ConnectionHolding> connectionHoldings(
            List<AssetBalance> balances,
            List<ConnectionEntity> connections,
            Map<UUID, ConnectionSyncState> balanceSync,
            MarketPriceQuote quote,
            Map<String, DataFreshness> fxFreshness) {
        Map<UUID, List<AssetBalance>> byConnection = balances.stream()
                .collect(Collectors.groupingBy(balance -> balance.getConnection().getId()));
        Map<UUID, ConnectionEntity> connectionsById = connections.stream()
                .collect(Collectors.toMap(ConnectionEntity::getId, connection -> connection));
        return byConnection.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<AssetBalance> connectionBalances = entry.getValue();
                    ConnectionEntity connection = connectionsById.get(entry.getKey());
                    ConnectionSyncState syncState = balanceSync.get(entry.getKey());
                    BigDecimal quantity = connectionBalances.stream()
                            .map(AssetBalance::getTotalQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    boolean everyValued = connectionBalances.stream().allMatch(balance ->
                            balance.getValuationStatus() == ValuationStatus.VALUED && balance.getJpyValue() != null);
                    boolean anyValued = connectionBalances.stream().anyMatch(balance ->
                            balance.getValuationStatus() == ValuationStatus.VALUED && balance.getJpyValue() != null);
                    boolean hasSyncHistory = hasSuccessfulBalanceSync(syncState);
                    BigDecimal valueJpy = everyValued && hasSyncHistory
                            ? connectionBalances.stream().map(AssetBalance::getJpyValue)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                            : null;
                    boolean stale = connectionBalances.stream()
                            .anyMatch(balance -> stale(balance, balanceSync, quote, fxFreshness));
                    AssetDataStatus status = aggregateStatus(
                            everyValued && hasSyncHistory, anyValued && hasSyncHistory,
                            stale, connectionBalances.size());
                    Instant fetchedAt = connectionBalances.stream()
                            .map(AssetBalance::getFetchedAt)
                            .filter(Objects::nonNull)
                            .min(Instant::compareTo)
                            .orElse(null);
                    return new AssetsResponse.ConnectionHolding(
                            connection.getId(),
                            connection.getProvider(),
                            connection.getDisplayName(),
                            quantity,
                            valueJpy,
                            status,
                            fetchedAt,
                            syncState == null ? null : syncState.getLastSuccessAt());
                })
                .toList();
    }

    private boolean stale(
            AssetBalance balance,
            Map<UUID, ConnectionSyncState> balanceSync,
            MarketPriceQuote quote,
            Map<String, DataFreshness> fxFreshness) {
        ConnectionSyncState sync = balanceSync.get(balance.getConnection().getId());
        if (hasSuccessfulBalanceSync(sync) && !isReady(sync)) {
            return true;
        }
        if (balance.getTotalQuantity().signum() == 0
                || ("BITBANK".equals(balance.getNetwork()) && "JPY".equals(balance.getAssetKey()))) {
            return false;
        }
        if (quote != null && quote.freshness() == DataFreshness.STALE) {
            return true;
        }
        String currency = balance.getPriceCurrency();
        return currency != null && fxFreshness.get(currency) == DataFreshness.STALE;
    }

    private AssetsResponse.Price priceResponse(MarketPriceQuote quote, List<AssetBalance> balances) {
        if (quote != null) {
            return new AssetsResponse.Price(
                    quote.price().map(price -> price.amount()).orElse(null),
                    quote.price().map(price -> price.currency().value()).orElse(null),
                    quote.source().map(Enum::name).orElse(null),
                    quote.evaluatedAt().orElse(null),
                    freshnessStatus(quote.freshness()),
                    quote.failureCategory().map(Enum::name).orElse(null));
        }
        boolean yenIdentity = balances.stream().allMatch(balance ->
                "BITBANK".equals(balance.getNetwork()) && "JPY".equals(balance.getAssetKey()));
        return yenIdentity
                ? new AssetsResponse.Price(BigDecimal.ONE, "JPY", "IDENTITY", null,
                        AssetDataStatus.COMPLETE, null)
                : new AssetsResponse.Price(null, null, null, null, AssetDataStatus.UNAVAILABLE, null);
    }

    private AssetsResponse.PriceChange changeResponse(MarketPriceQuote quote) {
        if (quote == null) {
            return new AssetsResponse.PriceChange(
                    null, "PERCENTAGE", "H24", null, null, AssetDataStatus.UNAVAILABLE, null);
        }
        var change = quote.change24h().orElse(null);
        AssetDataStatus status = change != null ? AssetDataStatus.COMPLETE
                : quote.freshness() == DataFreshness.STALE ? AssetDataStatus.STALE
                : AssetDataStatus.UNAVAILABLE;
        return new AssetsResponse.PriceChange(
                change == null ? null : change.value(),
                change == null ? "PERCENTAGE" : change.unit().name(),
                change == null ? "H24" : change.comparisonPeriod().name(),
                quote.source().map(Enum::name).orElse(null),
                quote.evaluatedAt().orElse(null),
                status,
                quote.failureCategory().map(Enum::name).orElse(null));
    }

    private AssetsResponse.Valuation valuation(List<AssetBalance> balances) {
        List<String> fxSources = balances.stream()
                .map(AssetBalance::getFxSource)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        Instant evaluatedAt = balances.stream()
                .map(AssetBalance::getFxEvaluatedAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        String source = fxSources.isEmpty() ? null : fxSources.size() == 1 ? fxSources.getFirst() : "MULTIPLE";
        return new AssetsResponse.Valuation("JPY", source, evaluatedAt);
    }

    private String displayName(AssetIdentity identity, List<AssetBalance> balances) {
        return balances.stream().map(AssetBalance::getAssetName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(identity.symbol());
    }

    private AssetIdentity identity(AssetBalance balance) {
        Optional<AssetMarketMapping.Identity> marketIdentity = AssetMarketMapping.resolve(
                balance.getAssetKey(), balance.getNetwork(), balance.getAssetRef());
        if (marketIdentity.isPresent()) {
            AssetMarketMapping.Identity market = marketIdentity.orElseThrow();
            return new AssetIdentity(
                    "MARKET:" + market.canonicalAssetKey(),
                    market.canonicalAssetKey(),
                    market.canonicalAssetKey(),
                    market.category(),
                    Optional.of(market.canonicalAssetKey()));
        }
        String network = balance.getNetwork() == null || balance.getNetwork().isBlank()
                ? "UNKNOWN" : balance.getNetwork().toUpperCase(java.util.Locale.ROOT);
        String externalIdentity = balance.getAssetRef() == null || balance.getAssetRef().isBlank()
                ? "ASSET:" + balance.getAssetKey()
                : "REF:" + balance.getAssetRef();
        return new AssetIdentity(
                "RAW:" + network + ":" + externalIdentity,
                balance.getAssetKey(),
                balance.getSymbol(),
                balance.getAssetCategory(),
                Optional.empty());
    }

    private BigDecimal sumCategory(List<AssetBuild> assets, AssetCategory category) {
        List<AssetsResponse.Asset> selected = assets.stream()
                .map(AssetBuild::response)
                .filter(asset -> category == null || asset.category() == category)
                .toList();
        if (selected.stream().anyMatch(asset -> asset.valueJpy() == null)) {
            return null;
        }
        return selected.stream().map(AssetsResponse.Asset::valueJpy).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AssetDataStatus summaryStatus(
            List<ConnectionEntity> connections,
            boolean allConnectionsHaveHistory,
            boolean anyConnectionHasHistory,
            boolean allConnectionsCurrentlyReady,
            boolean everyValuationAvailable,
            boolean anyValuationAvailable,
            boolean staleValuation) {
        if (connections.isEmpty()) {
            return AssetDataStatus.UNAVAILABLE;
        }
        if (!allConnectionsHaveHistory) {
            return anyConnectionHasHistory ? AssetDataStatus.PARTIAL : AssetDataStatus.UNAVAILABLE;
        }
        if (!everyValuationAvailable) {
            return anyValuationAvailable ? AssetDataStatus.PARTIAL : AssetDataStatus.UNAVAILABLE;
        }
        return !allConnectionsCurrentlyReady || staleValuation
                ? AssetDataStatus.STALE : AssetDataStatus.COMPLETE;
    }

    private AssetDataStatus aggregateStatus(
            boolean everyValuationAvailable,
            boolean anyValuationAvailable,
            boolean stale,
            int componentCount) {
        if (componentCount == 0 || !anyValuationAvailable) {
            return AssetDataStatus.UNAVAILABLE;
        }
        if (!everyValuationAvailable) {
            return AssetDataStatus.PARTIAL;
        }
        return stale ? AssetDataStatus.STALE : AssetDataStatus.COMPLETE;
    }

    private AssetDataStatus freshnessStatus(DataFreshness freshness) {
        return switch (freshness) {
            case FRESH -> AssetDataStatus.COMPLETE;
            case STALE -> AssetDataStatus.STALE;
            case UNAVAILABLE -> AssetDataStatus.UNAVAILABLE;
        };
    }

    private boolean hasSuccessfulBalanceSync(ConnectionSyncState state) {
        return state != null && state.getLastSuccessAt() != null;
    }

    private boolean isReady(ConnectionSyncState state) {
        return state != null && state.getStatus() == ConnectionSyncStatus.READY
                && state.getLastSuccessAt() != null;
    }

    private String commonValue(Collection<String> values) {
        List<String> distinct = values.stream().filter(Objects::nonNull).distinct().toList();
        return distinct.size() == 1 ? distinct.getFirst() : null;
    }

    private record AssetIdentity(
            String assetId,
            String assetKey,
            String symbol,
            AssetCategory category,
            Optional<String> marketKey) {
    }

    private record AssetBuild(AssetsResponse.Asset response, boolean stale) {
    }
}
