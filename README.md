# Resilient WebClient builder
Implements simple approach with retry & circuit breaker policies

# Usage
```
var client = new EnhancedWebClientBuilder(Duration.ofMillis(100), "http://localhost:8080")
                .withCircuitBreaker(50, Duration.ofMillis(100), 2, 2)
                .withRetry((byte) 1, Duration.ofMillis(50), 0.5)
                .build();
```
