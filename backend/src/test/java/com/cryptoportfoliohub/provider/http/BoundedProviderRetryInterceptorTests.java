package com.cryptoportfoliohub.provider.http;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedProviderRetryInterceptorTests {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void retriesTransientResponsesWithExponentialBackoffAndStopsAfterThreeAttempts() throws IOException {
        List<Duration> delays = new ArrayList<>();
        BoundedProviderRetryInterceptor interceptor = interceptor(delays, bound -> bound);
        AtomicInteger attempts = new AtomicInteger();
        ClientHttpResponse success = response(HttpStatus.OK, null);
        List<StubResponse> retriedResponses = new ArrayList<>();

        ClientHttpResponse result = interceptor.intercept(request(), new byte[0], (request, body) ->
                attempts.incrementAndGet() < 3
                        ? retryableResponse(HttpStatus.SERVICE_UNAVAILABLE, retriedResponses)
                        : success);

        assertThat(result).isSameAs(success);
        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));
        assertThat(retriedResponses).allMatch(response -> response.closed);
    }

    @Test
    void doesNotRetryAuthenticationPermissionValidationOrOtherNonTransientStatuses() throws IOException {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.NOT_IMPLEMENTED)) {
            List<Duration> delays = new ArrayList<>();
            BoundedProviderRetryInterceptor interceptor = interceptor(delays, bound -> bound);
            AtomicInteger attempts = new AtomicInteger();

            ClientHttpResponse result = interceptor.intercept(request(), new byte[0], (request, body) -> {
                attempts.incrementAndGet();
                return response(status, null);
            });

            assertThat(result.getStatusCode()).isEqualTo(status);
            assertThat(attempts).hasValue(1);
            assertThat(delays).isEmpty();
        }
    }

    @Test
    void retriesTransientIoFailuresButDoesNotRetryTlsFailures() throws IOException {
        List<Duration> delays = new ArrayList<>();
        BoundedProviderRetryInterceptor interceptor = interceptor(delays, bound -> bound);
        AtomicInteger attempts = new AtomicInteger();
        ClientHttpResponse success = response(HttpStatus.OK, null);

        ClientHttpResponse result = interceptor.intercept(request(), new byte[0], (request, body) -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("network failure");
            }
            return success;
        });

        assertThat(result).isSameAs(success);
        assertThat(attempts).hasValue(2);
        assertThat(delays).containsExactly(Duration.ofMillis(200));

        attempts.set(0);
        delays.clear();
        IOException repeatedNetworkFailure = new IOException("network failure");
        assertThatThrownBy(() -> interceptor.intercept(request(), new byte[0], (request, body) -> {
            attempts.incrementAndGet();
            throw repeatedNetworkFailure;
        })).isSameAs(repeatedNetworkFailure);
        assertThat(attempts).hasValue(3);
        assertThat(delays).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));

        attempts.set(0);
        delays.clear();
        SSLException tlsFailure = new SSLException("TLS failure");
        assertThatThrownBy(() -> interceptor.intercept(request(), new byte[0], (request, body) -> {
            attempts.incrementAndGet();
            throw tlsFailure;
        })).isSameAs(tlsFailure);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void honorsRetryAfterDeltaSecondsAndHttpDate() throws IOException {
        List<Duration> delays = new ArrayList<>();
        BoundedProviderRetryInterceptor interceptor = interceptor(delays, bound -> 0);
        AtomicInteger attempts = new AtomicInteger();
        ClientHttpResponse success = response(HttpStatus.OK, null);

        interceptor.intercept(request(), new byte[0], (request, body) ->
                attempts.incrementAndGet() == 1
                        ? response(HttpStatus.TOO_MANY_REQUESTS, "1")
                        : success);

        assertThat(delays).containsExactly(Duration.ofSeconds(1));

        delays.clear();
        attempts.set(0);
        String retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.format(NOW.plusSeconds(2).atZone(ZoneOffset.UTC));
        interceptor.intercept(request(), new byte[0], (request, body) ->
                attempts.incrementAndGet() == 1
                        ? response(HttpStatus.SERVICE_UNAVAILABLE, retryAt)
                        : success);

        assertThat(delays).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void doesNotRetryBeforeRetryAfterWhenItsDelayExceedsTheConfiguredBound() throws IOException {
        List<Duration> delays = new ArrayList<>();
        BoundedProviderRetryInterceptor interceptor = interceptor(delays, bound -> bound);
        AtomicInteger attempts = new AtomicInteger();
        ClientHttpResponse rateLimited = response(HttpStatus.TOO_MANY_REQUESTS, "3");

        ClientHttpResponse result = interceptor.intercept(request(), new byte[0], (request, body) -> {
            attempts.incrementAndGet();
            return rateLimited;
        });

        assertThat(result).isSameAs(rateLimited);
        assertThat(attempts).hasValue(1);
        assertThat(delays).isEmpty();
    }

    @Test
    void restoresInterruptAndAbortsRetryWithoutIncludingTransportDetails() throws IOException {
        BoundedProviderRetryInterceptor interceptor = new BoundedProviderRetryInterceptor(
                Clock.fixed(NOW, ZoneOffset.UTC), duration -> {
                    throw new InterruptedException();
                }, bound -> bound);
        AtomicInteger attempts = new AtomicInteger();
        try {
            assertThatThrownBy(() -> interceptor.intercept(request(), new byte[0], (request, body) -> {
                attempts.incrementAndGet();
                return response(HttpStatus.SERVICE_UNAVAILABLE, null);
            }))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Provider retry interrupted.")
                    .hasNoCause();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(attempts).hasValue(1);
        } finally {
            Thread.interrupted();
        }
    }

    private static BoundedProviderRetryInterceptor interceptor(List<Duration> delays,
            java.util.function.LongUnaryOperator jitter) {
        return new BoundedProviderRetryInterceptor(Clock.fixed(NOW, ZoneOffset.UTC), delays::add, jitter);
    }

    private static HttpRequest request() {
        return new StubRequest();
    }

    private static ClientHttpResponse response(HttpStatus status, String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) {
            headers.set(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return new StubResponse(status, headers);
    }

    private static StubResponse retryableResponse(HttpStatus status, List<StubResponse> responses) {
        StubResponse response = (StubResponse) response(status, null);
        responses.add(response);
        return response;
    }

    private static final class StubRequest implements HttpRequest {
        private final HttpHeaders headers = new HttpHeaders();

        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public java.net.URI getURI() {
            return java.net.URI.create("https://provider.example/read-only");
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }
    }

    private static final class StubResponse implements ClientHttpResponse {
        private final HttpStatus status;
        private final HttpHeaders headers;
        private boolean closed;

        private StubResponse(HttpStatus status, HttpHeaders headers) {
            this.status = status;
            this.headers = headers;
        }

        @Override
        public HttpStatus getStatusCode() {
            return status;
        }

        @Override
        public String getStatusText() {
            return status.getReasonPhrase();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public java.io.InputStream getBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
