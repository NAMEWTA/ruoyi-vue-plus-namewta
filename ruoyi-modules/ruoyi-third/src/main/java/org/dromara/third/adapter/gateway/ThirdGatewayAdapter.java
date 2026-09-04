package org.dromara.third.adapter.gateway;

import lombok.RequiredArgsConstructor;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.third.api.ThirdPartyFailureCategory;
import org.dromara.third.api.ThirdPartyGateway;
import org.dromara.third.api.ThirdPartyRequest;
import org.dromara.third.api.ThirdPartyResponse;
import org.dromara.third.domain.ThirdCredential;
import org.dromara.third.http.ThirdHttpClientFactory;
import org.dromara.third.port.ThirdConfigSnapshot;
import org.dromara.third.port.ThirdConfigSnapshotPort;
import org.dromara.third.port.ThirdCredentialStore;
import org.dromara.third.port.ThirdCredentialCryptoPort;
import org.dromara.third.port.ThirdInvocationRecorderPort;
import org.dromara.third.port.ThirdResiliencePort;
import org.dromara.third.support.ThirdEndpointSecurity;
import org.dromara.third.support.ThirdLimitLease;
import org.dromara.third.support.ThirdRejectedException;
import org.dromara.third.spi.ThirdAdapterRequest;
import org.dromara.third.spi.ThirdAdapterResponse;
import org.dromara.third.spi.ThirdProviderAdapter;
import org.dromara.third.spi.ThirdProviderAdapterRegistry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ThirdGatewayAdapter implements ThirdPartyGateway {
    private final ThirdConfigSnapshotPort configCache;
    private final ThirdProviderAdapterRegistry adapterRegistry;
    private final ThirdResiliencePort resiliencePolicy;
    private final ThirdInvocationRecorderPort invocationRecorder;
    private final ThirdCredentialStore credentialStore;
    private final ThirdCredentialCryptoPort credentialCrypto;
    private final ThirdHttpClientFactory clientFactory = new ThirdHttpClientFactory();

    @Override
    public ThirdPartyResponse<Object> execute(ThirdPartyRequest request) {
        return executeInternal(request, Object.class);
    }

    @Override
    public <T> ThirdPartyResponse<T> execute(ThirdPartyRequest request, Class<T> responseType) {
        return executeInternal(request, responseType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ThirdPartyResponse<T> execute(ThirdPartyRequest request, ParameterizedTypeReference<T> responseType) {
        if (responseType == null) throw new IllegalArgumentException("Response type is required");
        return (ThirdPartyResponse<T>) (ThirdPartyResponse<?>) executeInternal(request, Object.class, responseType.getType());
    }

    private <T> ThirdPartyResponse<T> executeInternal(ThirdPartyRequest request, Class<T> responseType) {
        return executeInternal(request, responseType, responseType);
    }

    @SuppressWarnings("unchecked")
    private <T> ThirdPartyResponse<T> executeInternal(ThirdPartyRequest request, Class<T> responseType, Type genericType) {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        ThirdConfigSnapshot snapshot;
        try {
            snapshot = configCache.get(request.providerCode(), request.endpointCode());
        } catch (RuntimeException e) {
            return failure(request, requestId, ThirdPartyFailureCategory.CONFIG_UNAVAILABLE, 0, null);
        }
        if (!"0".equals(snapshot.getProvider().getStatus())) return failure(request, requestId, ThirdPartyFailureCategory.PROVIDER_DISABLED, 0, null);
        if (!"0".equals(snapshot.getEndpoint().getStatus())) return failure(request, requestId, ThirdPartyFailureCategory.ENDPOINT_DISABLED, 0, null);
        ThirdLimitLease lease;
        try {
            lease = resiliencePolicy.acquire(snapshot.getProvider(), snapshot.getEndpoint());
        } catch (ThirdRejectedException e) {
            return failure(request, requestId, e.category(), 0, null);
        }
        int attemptsUsed = 0;
        try {
            String path = expandPath(snapshot.getEndpoint().getRelativePath(), request.path());
            validateDeclaredValues(snapshot.getEndpoint().getQuerySchemaJson(), request.query());
            validateBody(snapshot.getEndpoint().getBodySchemaJson(), request.body());
            Map<String, String> headers = declaredHeaders(snapshot.getEndpoint().getHeaderSchemaJson(), request.headers());
            mergeSharedHeaders(headers, snapshot.getProvider().getSharedHeadersJson());
            mergeCredentialHeaders(headers, snapshot);
            ThirdAdapterRequest prepared = new ThirdAdapterRequest(request, snapshot, headers, request.body());
            ThirdProviderAdapter adapter = adapterRegistry.find(request.providerCode());
            if (adapter != null) prepared = adapter.prepare(prepared);
            RestClient client = clientFactory.create(snapshot.getProvider().getBaseUrl(), snapshot.getProvider().getTimeoutConnectMs(), snapshot.getProvider().getTimeoutReadMs());
            ResponseEntity<byte[]> response = null;
            int maxAttempts = resiliencePolicy.maxAttempts(snapshot.getEndpoint());
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                attemptsUsed = attempt;
                RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(snapshot.getEndpoint().getHttpMethod()))
                    .uri(uriBuilder -> uriBuilder.path(path).queryParams(query(request.query())).build())
                    .headers(h -> headers.forEach(h::set));
                if (prepared.body() != null && !"GET".equals(snapshot.getEndpoint().getHttpMethod())) {
                    if ("FORM".equals(snapshot.getEndpoint().getRequestMode())) {
                        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                        request.query().forEach((key, value) -> form.add(key, String.valueOf(value)));
                        spec.body(form);
                    } else {
                        spec.body(prepared.body() instanceof JsonNode node ? node.toString() : prepared.body());
                    }
                }
                try {
                    response = spec.retrieve().toEntity(byte[].class);
                    break;
                } catch (RestClientResponseException | org.springframework.web.client.ResourceAccessException e) {
                    if (attempt == maxAttempts) throw e;
                }
            }
            if (response == null) throw new RestClientException("No response from third-party endpoint");
            Object body = decode(response, snapshot.getEndpoint().getResponseMode());
            ThirdAdapterResponse mappedInput = new ThirdAdapterResponse(request, response.getStatusCode().value(), body,
                (System.nanoTime() - startedAt) / 1_000_000, requestId);
            if (adapter != null) {
                ThirdPartyResponse<?> mapped = adapter.mapResponse(mappedInput);
                if (mapped != null) {
                    invocationRecorder.record(request, mapped, (System.nanoTime() - startedAt) / 1_000_000, attemptsUsed);
                    return (ThirdPartyResponse<T>) mapped;
                }
            }
            ThirdPartyResponse<T> result = new ThirdPartyResponse<>(requestId, request.providerCode(), request.endpointCode(), response.getStatusCode().value(),
                ThirdPartyFailureCategory.NONE, null, convert(body, responseType, genericType));
            invocationRecorder.record(request, result, (System.nanoTime() - startedAt) / 1_000_000, attemptsUsed);
            return result;
        } catch (RestClientResponseException e) {
            return failure(request, requestId, ThirdPartyFailureCategory.HTTP, e.getStatusCode().value(), null,
                elapsedMs(startedAt), attemptsUsed);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            return failure(request, requestId, ThirdPartyFailureCategory.TIMEOUT, 0, null,
                elapsedMs(startedAt), attemptsUsed);
        } catch (ThirdRejectedException e) {
            return failure(request, requestId, e.category(), 0, null, elapsedMs(startedAt), attemptsUsed);
        } catch (IllegalArgumentException e) {
            return failure(request, requestId, ThirdPartyFailureCategory.REJECTED, 0, null,
                elapsedMs(startedAt), attemptsUsed);
        } catch (RestClientException e) {
            return failure(request, requestId, ThirdPartyFailureCategory.TRANSPORT, 0, null,
                elapsedMs(startedAt), attemptsUsed);
        } finally {
            lease.close();
        }
    }

    private static Object decode(ResponseEntity<byte[]> response, String mode) {
        byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
        if ("BYTES".equals(mode)) return body;
        if ("TEXT".equals(mode)) return new String(body, StandardCharsets.UTF_8);
        return body.length == 0 ? null : JsonUtils.getJsonMapper().readTree(body);
    }

    private static <T> T convert(Object body, Class<T> type, Type genericType) {
        if (body == null || (type == Object.class && genericType == Object.class)) return type.cast(body);
        if (genericType == type && type.isInstance(body)) return type.cast(body);
        if (genericType == Object.class) return type.cast(body);
        return (T) JsonUtils.getJsonMapper().convertValue(body, JsonUtils.getJsonMapper().constructType(genericType));
    }

    private static String expandPath(String template, Map<String, ?> values) {
        String result = ThirdEndpointSecurity.validateRelativePath(template);
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            if (!result.contains(token)) throw new IllegalArgumentException("Unknown path parameter");
            result = result.replace(token, UriUtils.encodePathSegment(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        if (result.matches(".*\\{[^}]+}.*")) throw new IllegalArgumentException("Missing path parameter");
        return result;
    }

    private static Map<String, String> declaredHeaders(String schema, Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();
        java.util.Set<String> allowed = schema == null ? java.util.Set.of() : parseNames(schema);
        for (Map.Entry<String, String> entry : input.entrySet()) {
            if (!allowed.contains(entry.getKey().toLowerCase())) throw new IllegalArgumentException("Header is not declared");
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static java.util.Set<String> parseNames(String schema) {
        JsonNode node = JsonUtils.getJsonMapper().readTree(schema);
        java.util.Set<String> result = new java.util.HashSet<>();
        if (node != null && node.isArray()) node.forEach(value -> result.add(value.asText().toLowerCase()));
        else if (node != null && node.isObject() && node.get("allowed") != null && node.get("allowed").isArray()) node.get("allowed").forEach(value -> result.add(value.asText().toLowerCase()));
        return result;
    }

    private static void mergeSharedHeaders(Map<String, String> target, String json) {
        if (json == null || json.isBlank()) return;
        JsonNode node = JsonUtils.getJsonMapper().readTree(json);
        if (node != null && node.isObject()) node.properties().forEach(entry -> target.putIfAbsent(entry.getKey(), entry.getValue().asText()));
    }

    private static MultiValueMap<String, String> query(Map<String, ?> values) {
        MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
        values.forEach((key, value) -> { if (value != null) result.add(key, String.valueOf(value)); });
        return result;
    }

    private void mergeCredentialHeaders(Map<String, String> target, ThirdConfigSnapshot snapshot) {
        java.util.List<ThirdCredential> credentials = credentialStore.findByScopes(snapshot.getProvider().getProviderId(), snapshot.getEndpoint().getEndpointId());
        java.util.Map<String, ThirdCredential> byType = new java.util.LinkedHashMap<>();
        credentials.forEach(credential -> byType.putIfAbsent(credential.getCredentialType(), credential));
        credentials.forEach(credential -> { if (credential.getEndpointId() != null) byType.put(credential.getCredentialType(), credential); });
        byType.values().forEach(credential -> {
            if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new ThirdRejectedException(ThirdPartyFailureCategory.CONFIG_UNAVAILABLE, "Third-party credential expired");
            }
            JsonNode node = JsonUtils.getJsonMapper().readTree(credentialCrypto.decrypt(credential));
            if (node != null && node.isObject() && node.get("headers") != null && node.get("headers").isObject()) {
                node.get("headers").properties().forEach(entry -> target.putIfAbsent(entry.getKey(), entry.getValue().asText()));
            }
        });
    }

    private static void validateDeclaredValues(String schema, Map<String, ?> values) {
        if (values.isEmpty() || schema == null || schema.isBlank()) return;
        java.util.Set<String> allowed = parseNames(schema);
        if (values.keySet().stream().anyMatch(key -> !allowed.contains(key.toLowerCase()))) throw new IllegalArgumentException("Parameter is not declared");
    }

    private static void validateBody(String schema, JsonNode body) {
        if (body == null || !body.isObject() || schema == null || schema.isBlank()) return;
        java.util.Set<String> allowed = parseNames(schema);
        body.propertyNames().forEach(name -> { if (!allowed.contains(name.toLowerCase())) throw new IllegalArgumentException("Body field is not declared"); });
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private <T> ThirdPartyResponse<T> failure(ThirdPartyRequest request, String requestId,
                                              ThirdPartyFailureCategory category, int status, String message) {
        return failure(request, requestId, category, status, message, 0, 0);
    }

    private <T> ThirdPartyResponse<T> failure(ThirdPartyRequest request, String requestId,
                                              ThirdPartyFailureCategory category, int status, String message,
                                              long durationMs, int attempts) {
        ThirdPartyResponse<T> result = new ThirdPartyResponse<>(requestId, request.providerCode(), request.endpointCode(), status, category, message, null);
        try {
            invocationRecorder.record(request, result, durationMs, attempts);
        } catch (RuntimeException ignored) {
            // A logging failure must not replace the stable gateway classification.
        }
        return result;
    }
}
