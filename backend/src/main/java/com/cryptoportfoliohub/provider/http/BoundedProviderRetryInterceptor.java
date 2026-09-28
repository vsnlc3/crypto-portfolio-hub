package com.cryptoportfoliohub.provider.http;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import javax.net.ssl.SSLException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

/**
 * Bounded retry policy for the application's read-only provider HTTP clients.
 *
 * <p>Provider request URIs, headers, bodies, and transport exceptions are deliberately not logged here.
 */
@Component
public class BoundedProviderRetryInterceptor implements ClientHttpRequestInterceptor {

    static final int MAX_ATTEMPTS = 3;
    static final Duration BASE_BACKOFF = Duration.ofMillis(200);
    static final Duration MAX_BACKOFF = Duration.ofSeconds(2);

    private final Clock clock;
    private final RetrySleeper sleeper;
    private final LongUnaryOperator jitter;

    public BoundedProviderRetryInterceptor() {
        this(Clock.systemUTC(), duration -> Thread.sleep(duration.toMillis()),
                bound -> ThreadLocalRandom.current().nextLong(bound + 1));
    }

    BoundedProviderRetryInterceptor(Clock clock, RetrySleeper sleeper, LongUnaryOperator jitter) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    @Override
    public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        for (int attempt = 1; ; attempt++) {
            final ClientHttpResponse response;
            try {
                response = execution.execute(request, body);
            } catch (IOException exception) {
                if (attempt >= MAX_ATTEMPTS || Thread.currentThread().isInterrupted()
                        || !isRetryableTransportFailure(exception)) {
                    throw exception;
                }
                sleep(backoffForRetry(attempt));
                continue;
            }

            if (attempt >= MAX_ATTEMPTS || !isRetryableStatus(response.getStatusCode())) {
                return response;
            }

            Optional<Duration> retryAfter = retryAfter(response.getHeaders(), clock.instant());
            Duration backoff = backoffForRetry(attempt);
            if (retryAfter.filter(delay -> delay.compareTo(MAX_BACKOFF) > 0).isPresent()) {
                return response;
            }
            Duration delay = retryAfter.map(value -> value.compareTo(backoff) > 0 ? value : backoff)
                    .orElse(backoff);
            response.close();
            sleep(delay);
        }
    }

    private Duration backoffForRetry(int attempt) {
        long upperBoundMillis = Math.min(MAX_BACKOFF.toMillis(), BASE_BACKOFF.toMillis() << (attempt - 1));
        long randomDelayMillis = Math.max(0, Math.min(upperBoundMillis, jitter.applyAsLong(upperBoundMillis)));
        return Duration.ofMillis(randomDelayMillis);
    }

    private void sleep(Duration delay) throws IOException {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Provider retry interrupted.");
        }
    }

    private static boolean isRetryableTransportFailure(IOException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRetryableStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 408, 429, 500, 502, 503, 504 -> true;
            default -> false;
        };
    }

    private static Optional<Duration> retryAfter(HttpHeaders headers, Instant now) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return seconds < 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException | ArithmeticException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(now, retryAt);
                return Optional.of(delay.isNegative() ? Duration.ZERO : delay);
            } catch (RuntimeException invalidHttpDate) {
                return Optional.empty();
            }
        }
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
