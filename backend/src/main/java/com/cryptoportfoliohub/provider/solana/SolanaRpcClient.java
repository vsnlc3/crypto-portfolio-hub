package com.cryptoportfoliohub.provider.solana;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.domain.solana.SolanaAddress;
import com.cryptoportfoliohub.marketdata.MarketDataHttpErrors;
import com.cryptoportfoliohub.provider.solana.config.SolanaProviderProperties;
import java.math.BigInteger;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Component
public class SolanaRpcClient {

    private static final BigInteger MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    private static final String TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";

    private final RestClient restClient;
    private final SolanaProviderProperties properties;

    public SolanaRpcClient(@Qualifier("solanaRestClient") RestClient solanaRestClient,
            SolanaProviderProperties properties) {
        this.restClient = solanaRestClient;
        this.properties = properties;
    }

    public BigInteger getBalanceLamports(SolanaAddress address) {
        JsonNode result = rpc("getBalance", List.of(address.value(), Map.of("commitment", "finalized")));
        return nonNegativeInteger(result.path("value"));
    }

    public List<TokenAccount> getTokenAccounts(SolanaAddress address, String programId) {
        if (!TOKEN_PROGRAM.equals(programId) && !TOKEN_2022_PROGRAM.equals(programId)) {
            throw new IllegalArgumentException("Unsupported Solana token program.");
        }
        JsonNode result = rpc("getTokenAccountsByOwner", List.of(
                address.value(),
                Map.of("programId", programId),
                Map.of("encoding", "jsonParsed", "commitment", "finalized")));
        JsonNode value = result.path("value");
        if (!value.isArray()) {
            throw invalidResponse();
        }
        java.util.ArrayList<TokenAccount> accounts = new java.util.ArrayList<>();
        for (JsonNode row : value) {
            JsonNode info = row.path("account").path("data").path("parsed").path("info");
            String mint = text(info.path("mint"));
            JsonNode tokenAmount = info.path("tokenAmount");
            BigInteger rawAmount = nonNegativeInteger(tokenAmount.path("amount"));
            int decimals = exactInt(tokenAmount.path("decimals"));
            if (mint.isBlank() || decimals < 0 || decimals > 255) {
                throw invalidResponse();
            }
            accounts.add(new TokenAccount(mint, rawAmount, decimals));
        }
        return List.copyOf(accounts);
    }

    private JsonNode rpc(String method, List<?> params) {
        URI uri;
        try {
            uri = UriComponentsBuilder.fromUriString(properties.getRpcUrl()).build().encode().toUri();
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", "crypto-portfolio-hub",
                "method", method,
                "params", params);
        final JsonNode body;
        try {
            body = restClient.post().uri(uri).body(request).retrieve().body(JsonNode.class);
        } catch (HttpStatusCodeException exception) {
            throw new ProviderException(MarketDataHttpErrors.forStatus(exception.getStatusCode()));
        } catch (ResourceAccessException exception) {
            throw new ProviderException(MarketDataHttpErrors.forTransport(exception));
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
        if (body == null || body.hasNonNull("error") || !body.hasNonNull("result")) {
            throw invalidResponse();
        }
        return body.get("result");
    }

    private static BigInteger nonNegativeInteger(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw invalidResponse();
        }
        try {
            BigInteger value = new BigInteger(node.asText());
            if (value.signum() < 0 || value.compareTo(MAX_U64) > 0) {
                throw invalidResponse();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static int exactInt(JsonNode node) {
        try {
            return Integer.parseInt(node.asText());
        } catch (NumberFormatException exception) {
            throw invalidResponse();
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static ProviderException invalidResponse() {
        return new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
    }

    public record TokenAccount(String mint, BigInteger rawAmount, int decimals) {
    }
}
