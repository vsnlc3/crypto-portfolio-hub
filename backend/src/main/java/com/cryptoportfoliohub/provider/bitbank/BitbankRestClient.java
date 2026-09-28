package com.cryptoportfoliohub.provider.bitbank;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.marketdata.MarketDataHttpErrors;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Component
public final class BitbankRestClient {

    private static final String BASE_URL = "https://api.bitbank.cc/v1";
    private static final int ACCESS_TIME_WINDOW_MILLIS = 5_000;
    private static final int MAX_TRADES = 1_000;
    private static final int MAX_TRANSFERS = 100;

    private final RestClient restClient;
    private final Clock clock;

    public BitbankRestClient(
            @Qualifier("bitbankHttpRestClient") RestClient bitbankRestClient,
            Clock clock) {
        this.restClient = bitbankRestClient;
        this.clock = clock;
    }

    public List<BitbankDtos.Asset> fetchAssets(BitbankCredentials credentials) {
        JsonNode rows = dataArray(getPrivate("/user/assets", Map.of(), credentials), "assets");
        List<BitbankDtos.Asset> assets = new ArrayList<>();
        for (JsonNode row : rows) {
            assets.add(new BitbankDtos.Asset(
                    requiredText(row, "asset"),
                    decimal(row, "free_amount"),
                    decimal(row, "onhand_amount"),
                    decimal(row, "locked_amount"),
                    decimal(row, "withdrawing_amount"),
                    optionalInteger(row, "amount_precision")));
        }
        return List.copyOf(assets);
    }

    public List<BitbankDtos.SpotPair> fetchSpotPairs() {
        JsonNode rows = dataArray(getPublic("/spot/pairs", Map.of()), "pairs");
        List<BitbankDtos.SpotPair> pairs = new ArrayList<>();
        for (JsonNode row : rows) {
            pairs.add(new BitbankDtos.SpotPair(
                    requiredText(row, "name"),
                    requiredText(row, "base_asset"),
                    requiredText(row, "quote_asset")));
        }
        return List.copyOf(pairs);
    }

    public List<BitbankDtos.Trade> fetchTrades(
            BitbankCredentials credentials, long sinceMillis, long endMillis) {
        Map<String, Object> params = historyParams(sinceMillis, endMillis, MAX_TRADES);
        params.put("order", "asc");
        JsonNode rows = dataArray(getPrivate("/user/spot/trade_history", params, credentials), "trades");
        List<BitbankDtos.Trade> trades = new ArrayList<>();
        for (JsonNode row : rows) {
            trades.add(new BitbankDtos.Trade(
                    requiredIdentifier(row, "trade_id"),
                    requiredText(row, "pair"),
                    requiredText(row, "side"),
                    optionalText(row, "position_side"),
                    requiredText(row, "type"),
                    decimal(row, "amount"),
                    decimal(row, "price"),
                    decimal(row, "fee_amount_base"),
                    decimal(row, "fee_amount_quote"),
                    decimal(row, "fee_occurred_amount_quote"),
                    requiredLong(row, "executed_at")));
        }
        return List.copyOf(trades);
    }

    public List<BitbankDtos.Deposit> fetchDeposits(
            BitbankCredentials credentials, String asset, long sinceMillis, long endMillis) {
        Map<String, Object> params = historyParams(sinceMillis, endMillis, MAX_TRANSFERS);
        if (asset != null) {
            params.put("asset", asset);
        }
        JsonNode rows = dataArray(getPrivate("/user/deposit_history", params, credentials), "deposits");
        List<BitbankDtos.Deposit> deposits = new ArrayList<>();
        for (JsonNode row : rows) {
            deposits.add(new BitbankDtos.Deposit(
                    requiredText(row, "uuid"),
                    requiredText(row, "asset"),
                    optionalText(row, "network"),
                    decimal(row, "amount"),
                    optionalText(row, "txid"),
                    optionalText(row, "status"),
                    optionalText(row, "category"),
                    requiredLong(row, "found_at"),
                    optionalLong(row, "confirmed_at")));
        }
        return List.copyOf(deposits);
    }

    public List<BitbankDtos.Withdrawal> fetchWithdrawals(
            BitbankCredentials credentials, String asset, long sinceMillis, long endMillis) {
        Map<String, Object> params = historyParams(sinceMillis, endMillis, MAX_TRANSFERS);
        if (asset != null) {
            params.put("asset", asset);
        }
        JsonNode rows = dataArray(getPrivate("/user/withdrawal_history", params, credentials), "withdrawals");
        List<BitbankDtos.Withdrawal> withdrawals = new ArrayList<>();
        for (JsonNode row : rows) {
            withdrawals.add(new BitbankDtos.Withdrawal(
                    requiredText(row, "uuid"),
                    requiredText(row, "asset"),
                    decimal(row, "amount"),
                    decimal(row, "fee"),
                    optionalText(row, "network"),
                    optionalText(row, "txid"),
                    optionalText(row, "status"),
                    requiredLong(row, "requested_at")));
        }
        return List.copyOf(withdrawals);
    }

    private JsonNode getPublic(String path, Map<String, ?> params) {
        return get(path, params, null);
    }

    private JsonNode getPrivate(String path, Map<String, ?> params, BitbankCredentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("Credentials are required for a private request.");
        }
        return get(path, params, credentials);
    }

    private JsonNode get(String path, Map<String, ?> params, BitbankCredentials credentials) {
        URI uri = uri(path, params);
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get().uri(uri);
            if (credentials != null) {
                long requestTimeMillis = clock.millis();
                String signature = signature(credentials, requestTimeMillis, uri);
                request = request.headers(headers -> addAuthHeaders(
                        headers, credentials.apiKeyHeaderValue(), requestTimeMillis, signature));
            }
            JsonNode body = request.retrieve().body(JsonNode.class);
            JsonNode data = envelopeData(body);
            return data;
        } catch (ProviderException exception) {
            throw exception;
        } catch (HttpStatusCodeException exception) {
            throw new ProviderException(MarketDataHttpErrors.forStatus(exception.getStatusCode()));
        } catch (ResourceAccessException exception) {
            throw new ProviderException(MarketDataHttpErrors.forTransport(exception));
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    private static void addAuthHeaders(HttpHeaders headers, String apiKey, long requestTimeMillis, String signature) {
        headers.set("ACCESS-KEY", apiKey);
        headers.set("ACCESS-SIGNATURE", signature);
        headers.set("ACCESS-REQUEST-TIME", Long.toString(requestTimeMillis));
        headers.set("ACCESS-TIME-WINDOW", Integer.toString(ACCESS_TIME_WINDOW_MILLIS));
    }

    private static String signature(BitbankCredentials credentials, long requestTimeMillis, URI uri) {
        String pathAndQuery = uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        byte[] secret = credentials.copyApiSecret();
        try {
            return BitbankRequestSigner.sign(secret, requestTimeMillis, ACCESS_TIME_WINDOW_MILLIS, pathAndQuery);
        } finally {
            java.util.Arrays.fill(secret, (byte) 0);
        }
    }

    private static URI uri(String path, Map<String, ?> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(BASE_URL).path(path);
        params.forEach((name, value) -> builder.queryParam(name, value));
        try {
            return builder.build().encode().toUri();
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private static JsonNode envelopeData(JsonNode body) {
        if (body == null || !body.isObject() || !body.has("success")) {
            throw invalidResponse();
        }
        int success = exactInt(body.get("success"));
        if (success != 1) {
            JsonNode codeNode = body.path("data").path("code");
            int code = exactInt(codeNode);
            throw new ProviderException(errorForBitbankCode(code));
        }
        JsonNode data = body.get("data");
        if (data == null || !data.isObject()) {
            throw invalidResponse();
        }
        return data;
    }

    private static ProviderErrorCategory errorForBitbankCode(int code) {
        if ((code >= 20_001 && code <= 20_005)
                || (code >= 20_018 && code <= 20_019)
                || (code >= 20_033 && code <= 20_039)) {
            return ProviderErrorCategory.AUTHENTICATION;
        }
        if (code == 10_005) {
            return ProviderErrorCategory.TIMEOUT;
        }
        if (code == 10_009) {
            return ProviderErrorCategory.RATE_LIMIT;
        }
        if (code >= 50_003 && code <= 50_008) {
            return ProviderErrorCategory.PERMISSION;
        }
        if (code == 10_001 || code == 10_003 || code == 10_007 || code == 10_008) {
            return ProviderErrorCategory.UNAVAILABLE;
        }
        return ProviderErrorCategory.INVALID_RESPONSE;
    }

    private static JsonNode dataArray(JsonNode data, String field) {
        JsonNode rows = data.get(field);
        if (rows == null || !rows.isArray()) {
            throw invalidResponse();
        }
        return rows;
    }

    private static Map<String, Object> historyParams(long sinceMillis, long endMillis, int count) {
        if (sinceMillis < 0 || endMillis < sinceMillis) {
            throw new IllegalArgumentException("A valid millisecond history window is required.");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("count", count);
        params.put("since", sinceMillis);
        params.put("end", endMillis);
        return params;
    }

    private static String requiredIdentifier(JsonNode row, String field) {
        String value = scalarText(value(row, field));
        if (value.isBlank() || !value.chars().allMatch(Character::isDigit)) {
            throw invalidResponse();
        }
        return value;
    }

    private static String requiredText(JsonNode row, String field) {
        String value = scalarText(value(row, field));
        if (value.isBlank()) {
            throw invalidResponse();
        }
        return value;
    }

    private static String optionalText(JsonNode row, String field) {
        return scalarText(value(row, field));
    }

    private static JsonNode value(JsonNode row, String field) {
        if (row == null || !row.isObject()) {
            throw invalidResponse();
        }
        return row.get(field);
    }

    private static BigDecimal decimal(JsonNode row, String field) {
        String value = requiredText(row, field);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static Integer optionalInteger(JsonNode row, String field) {
        String value = optionalText(row, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static long requiredLong(JsonNode row, String field) {
        Long value = optionalLong(row, field);
        if (value == null) {
            throw invalidResponse();
        }
        return value;
    }

    private static Long optionalLong(JsonNode row, String field) {
        String value = optionalText(row, field);
        if (value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static int exactInt(JsonNode node) {
        String value = scalarText(node);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static String scalarText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || !node.isValueNode()) {
            return "";
        }
        return node.asText();
    }

    private static ProviderException invalidResponse() {
        return new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
    }
}
