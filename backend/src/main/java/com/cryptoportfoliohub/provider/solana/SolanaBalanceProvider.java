package com.cryptoportfoliohub.provider.solana;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.domain.solana.SolanaAddress;
import com.cryptoportfoliohub.provider.BalanceProvider;
import com.cryptoportfoliohub.provider.NormalizedAssetCategory;
import com.cryptoportfoliohub.provider.NormalizedAssetBalance;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class SolanaBalanceProvider implements BalanceProvider {

    private static final String NATIVE_SOL_KEY = "SOL";
    private static final String TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    private static final String TOKEN_2022_PROGRAM = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";

    private final SolanaRpcClient rpcClient;
    private final Clock clock;

    public SolanaBalanceProvider(SolanaRpcClient rpcClient, Clock marketDataClock) {
        this.rpcClient = rpcClient;
        this.clock = marketDataClock;
    }

    @Override
    public List<NormalizedAssetBalance> fetchBalances(String accountAddress) {
        SolanaAddress address = new SolanaAddress(accountAddress);
        BigDecimal nativeSol = decimal(rpcClient.getBalanceLamports(address), 9);
        Map<String, TokenBalance> tokenBalances = new TreeMap<>();
        for (String program : List.of(TOKEN_PROGRAM, TOKEN_2022_PROGRAM)) {
            for (SolanaRpcClient.TokenAccount account : rpcClient.getTokenAccounts(address, program)) {
                BigDecimal quantity = decimal(account.rawAmount(), account.decimals());
                TokenBalance prior = tokenBalances.get(account.mint());
                if (prior != null && prior.decimals() != account.decimals()) {
                    throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
                }
                tokenBalances.merge(account.mint(), new TokenBalance(quantity, account.decimals()),
                        (left, right) -> new TokenBalance(left.quantity().add(right.quantity()), left.decimals()));
            }
        }

        var fetchedAt = clock.instant();
        List<NormalizedAssetBalance> balances = new ArrayList<>();
        balances.add(new NormalizedAssetBalance(NATIVE_SOL_KEY, "SOL", "Solana", NormalizedAssetCategory.CRYPTO,
                "SOLANA", "NATIVE", nativeSol, fetchedAt));
        tokenBalances.forEach((mint, value) -> balances.add(new NormalizedAssetBalance(
                "SOLANA:" + mint, null, null, NormalizedAssetCategory.CRYPTO,
                "SOLANA", mint, value.quantity(), fetchedAt)));
        return List.copyOf(balances);
    }

    private static BigDecimal decimal(BigInteger rawAmount, int decimals) {
        BigDecimal value = new BigDecimal(rawAmount, decimals).stripTrailingZeros();
        if (value.scale() > 18) {
            // The current persistence schema stores asset quantities at scale 18. Never round an on-chain amount.
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }
        return value.scale() < 0 ? value.setScale(0) : value;
    }

    private record TokenBalance(BigDecimal quantity, int decimals) {
    }
}
