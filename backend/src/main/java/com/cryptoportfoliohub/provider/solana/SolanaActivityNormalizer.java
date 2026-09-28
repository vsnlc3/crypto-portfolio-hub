package com.cryptoportfoliohub.provider.solana;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.domain.solana.SolanaAddress;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import com.cryptoportfoliohub.provider.NormalizedActivityLeg;
import com.cryptoportfoliohub.provider.NormalizedActivityType;
import com.cryptoportfoliohub.provider.NormalizedDirection;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class SolanaActivityNormalizer {

    private static final BigInteger MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final String NATIVE_SOL_KEY = "SOL";

    public NormalizedActivity normalize(String walletAddress, JsonNode signatureRow, JsonNode parsedEvent) {
        SolanaAddress wallet = new SolanaAddress(walletAddress);
        String signature = text(signatureRow.path("signature"));
        if (signature.isBlank() || !signature.equals(text(parsedEvent.path("signature")))) {
            throw invalidResponse();
        }

        JsonNode parsed = parsedEvent.path("parsed");
        boolean hasParsed = parsed.isObject();
        String parserStatus = text(parsedEvent.path("parserStatus"));
        if (!"OK".equals(parserStatus) && !"ERROR".equals(parserStatus)) {
            throw invalidResponse();
        }
        boolean parserFailed = !"OK".equals(parserStatus);
        if (parserFailed == hasParsed) {
            throw invalidResponse();
        }
        boolean signatureFailed = signatureRow.hasNonNull("err");
        String transactionStatus = hasParsed ? text(parsed.path("transactionStatus")) : "";
        if (hasParsed && !"OK".equals(transactionStatus) && !"ERROR".equals(transactionStatus)) {
            throw invalidResponse();
        }
        boolean transactionFailed = signatureFailed || "ERROR".equals(transactionStatus);

        Instant occurredAt = epochInstant(hasParsed ? parsed.path("blockTime") : null);
        if (occurredAt == null) {
            occurredAt = epochInstant(signatureRow.path("blockTime"));
        }
        if (occurredAt == null) {
            throw invalidResponse();
        }

        List<NormalizedActivityLeg> legs = new ArrayList<>();
        String originalType = null;
        String status;
        if (hasParsed) {
            if (parsed.path("fee").isMissingNode() || parsed.path("fee").isNull()
                    || text(parsed.path("feePayer")).isBlank()
                    || !parsed.path("nativeTransfers").isArray()
                    || !parsed.path("tokenTransfers").isArray()) {
                throw invalidResponse();
            }
            originalType = text(parsed.path("summary").path("type"));
            if (!transactionFailed) {
                addNativeTransfers(wallet.value(), parsed.path("nativeTransfers"), legs);
                addTokenTransfers(wallet.value(), parsed.path("tokenTransfers"), legs);
            }
            BigInteger fee = integer(parsed.path("fee"));
            if (wallet.value().equals(text(parsed.path("feePayer"))) && fee.signum() > 0) {
                addLeg(legs, NormalizedDirection.FEE, NATIVE_SOL_KEY, "SOL", decimal(fee, 9), new BigDecimal(fee),
                        "lamports");
            }
            status = transactionFailed ? "FAILED" : "SUCCESS";
        } else {
            status = parserFailed ? "PARSER_ERROR" : "UNKNOWN";
        }

        NormalizedActivityType eventType = eventType(originalType, legs);
        return new NormalizedActivity(signature, "transaction:" + signature, eventType,
                originalType == null || originalType.isBlank() ? null : originalType,
                status, occurredAt, legs);
    }

    private static void addNativeTransfers(String wallet, JsonNode transfers, List<NormalizedActivityLeg> legs) {
        if (transfers == null || !transfers.isArray()) {
            throw invalidResponse();
        }
        for (JsonNode transfer : transfers) {
            String from = text(transfer.path("fromUserAccount"));
            String to = text(transfer.path("toUserAccount"));
            if (from.isBlank() || to.isBlank()) {
                throw invalidResponse();
            }
            if (from.equals(wallet) && to.equals(wallet)) {
                continue;
            }
            NormalizedDirection direction = direction(wallet, from, to);
            if (direction == null) {
                continue;
            }
            BigInteger raw = integer(transfer.path("amount"));
            addLeg(legs, direction, NATIVE_SOL_KEY, "SOL", decimal(raw, 9), new BigDecimal(raw), "lamports");
        }
    }

    private static void addTokenTransfers(String wallet, JsonNode transfers, List<NormalizedActivityLeg> legs) {
        if (transfers == null || !transfers.isArray()) {
            throw invalidResponse();
        }
        for (JsonNode transfer : transfers) {
            String from = text(transfer.path("fromUserAccount"));
            String to = text(transfer.path("toUserAccount"));
            if (from.isBlank() || to.isBlank()) {
                throw invalidResponse();
            }
            if (from.equals(wallet) && to.equals(wallet)) {
                continue;
            }
            NormalizedDirection direction = direction(wallet, from, to);
            if (direction == null) {
                continue;
            }
            String mint = text(transfer.path("mint"));
            int decimals = exactInt(transfer.path("decimals"));
            if (mint.isBlank() || decimals < 0 || decimals > 255) {
                throw invalidResponse();
            }
            BigInteger raw = integer(transfer.path("rawTokenAmount"));
            addLeg(legs, direction, "SOLANA:" + mint, null, decimal(raw, decimals), null, null);
        }
    }

    private static void addLeg(
            List<NormalizedActivityLeg> legs,
            NormalizedDirection direction,
            String assetKey,
            String symbol,
            BigDecimal quantity,
            BigDecimal originalAmount,
            String originalCurrency) {
        if (quantity.signum() > 0) {
            legs.add(new NormalizedActivityLeg(legs.size(), direction, assetKey, symbol,
                    quantity, originalAmount, originalCurrency));
        }
    }

    private static NormalizedDirection direction(String wallet, String from, String to) {
        if (wallet.equals(from)) {
            return NormalizedDirection.OUT;
        }
        if (wallet.equals(to)) {
            return NormalizedDirection.IN;
        }
        return null;
    }

    private static NormalizedActivityType eventType(String originalType, List<NormalizedActivityLeg> legs) {
        boolean hasIn = legs.stream().anyMatch(leg -> leg.direction() == NormalizedDirection.IN);
        boolean hasOut = legs.stream().anyMatch(leg -> leg.direction() == NormalizedDirection.OUT);
        if ("swap".equalsIgnoreCase(originalType) && hasIn && hasOut) {
            return NormalizedActivityType.SWAP;
        }
        if (hasIn || hasOut) {
            return NormalizedActivityType.TRANSFER;
        }
        return NormalizedActivityType.OTHER;
    }

    private static BigDecimal decimal(BigInteger raw, int decimals) {
        BigDecimal value = new BigDecimal(raw, decimals).stripTrailingZeros();
        if (value.scale() > 18) {
            // The current persistence schema stores quantities at scale 18. Never round on-chain values.
            throw invalidResponse();
        }
        return value.scale() < 0 ? value.setScale(0) : value;
    }

    private static BigInteger integer(JsonNode node) {
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

    private static Instant epochInstant(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(new BigInteger(node.asText()).longValueExact());
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static ProviderException invalidResponse() {
        return new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
    }
}
