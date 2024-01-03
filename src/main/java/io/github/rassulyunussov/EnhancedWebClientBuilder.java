package io.github.rassulyunussov;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class EnhancedWebClientBuilder {
    private final WebClient.Builder builder;

    EnhancedWebClientBuilder(final Duration timeout, final String baseUrl) {
        this.builder = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(getConnector(timeout));
    }

    public WebClient build() {
        return this.builder.filter(ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().is5xxServerError()) {
                return clientResponse.createError();
            }
            return Mono.just(clientResponse);
        })).build();
    }

    public EnhancedWebClientBuilder withRetry(final byte attempts, final Duration backoff, final double jitter) {
        final var retry = Retry.backoff(attempts, backoff).jitter(jitter);
        this.builder.filter((request, next) -> next.exchange(request)
                .retryWhen(retry));
        return this;
    }

    public EnhancedWebClientBuilder withCircuitBreaker(final float failureRateThreshold,
                                                       final Duration waitDurationInOpenState,
                                                       final int permittedNumberOfCallsInHalfOpenState,
                                                       final int slidingWindowSize) {

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .slidingWindowSize(slidingWindowSize)
                .build();

        final var cb = CircuitBreaker.of("web client circuit breaker", circuitBreakerConfig);
        this.builder.filter(((request, next) ->
                next.exchange(request).transform(CircuitBreakerOperator.of(cb))
        ));
        return this;
    }

    private static ClientHttpConnector getConnector(Duration timeout) {
        final var httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis())
                .responseTimeout(timeout)
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS)));
        return new ReactorClientHttpConnector(httpClient);
    }

}