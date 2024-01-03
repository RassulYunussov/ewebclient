package io.github.rassulyunussov;


import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class EnhancedWebClientBuilderTest {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    @Before
    public void initStup() {
        stubFor(get("/").willReturn(serverError()));
    }

    @Test
    public void testRetry() {
        var client = new EnhancedWebClientBuilder(Duration.ofMillis(100), "http://localhost:8080")
                .withCircuitBreaker(50, Duration.ofMillis(200), 1, 2)
                .withRetry((byte) 2, Duration.ofMillis(50), 0.5)
                .build();
        invokeAndSwallowException(client, 1);
        verify(exactly(3), getRequestedFor(urlEqualTo("/")));
    }

    @Test
    public void testCircuitBreaker() throws InterruptedException {
        var client = new EnhancedWebClientBuilder(Duration.ofMillis(100), "http://localhost:8080")
                .withCircuitBreaker(50, Duration.ofMillis(100), 2, 2)
                .build();
        invokeAndSwallowException(client, 4);
        Thread.sleep(100);
        invokeAndSwallowException(client, 4);
        verify(exactly(4), getRequestedFor(urlEqualTo("/")));
    }

    @Test
    public void testCircuitBreakerAndRetry() throws InterruptedException {
        var client = new EnhancedWebClientBuilder(Duration.ofMillis(100), "http://localhost:8080")
                .withCircuitBreaker(50, Duration.ofMillis(100), 2, 2)
                .withRetry((byte) 1, Duration.ofMillis(50), 0.5)
                .build();
        invokeAndSwallowException(client, 4);
        Thread.sleep(100);
        invokeAndSwallowException(client, 4);
        verify(exactly(8), getRequestedFor(urlEqualTo("/")));
    }

    static void invokeAndSwallowException(WebClient client, int times) {
        for (int i = 0; i < times; i++) {
            try {
                client.get().retrieve().bodyToMono(String.class).block();
            } catch (Exception ignored) {
            }
        }
    }
}
