package org.dromara.common.openapi.config;

import cn.dev33.satoken.stp.StpLogic;
import jakarta.servlet.DispatcherType;
import org.dromara.common.openapi.config.properties.OpenApiProperties;
import org.dromara.common.openapi.gateway.OpenApiGatewayFilter;
import org.dromara.common.openapi.nonce.OpenApiNonceStore;
import org.dromara.common.openapi.nonce.RedissonOpenApiNonceStore;
import org.dromara.common.openapi.protocol.OpenApiCanonicalizer;
import org.dromara.common.openapi.protocol.OpenApiSigner;
import org.dromara.common.openapi.ratelimit.OpenApiRateLimiter;
import org.dromara.common.openapi.ratelimit.RedissonOpenApiRateLimiter;
import org.dromara.common.openapi.registry.OpenApiAuthorizationMatcher;
import org.dromara.common.openapi.registry.OpenApiOperationRegistry;
import org.dromara.common.openapi.registry.SpringDocOperationSchemaResolver;
import org.dromara.common.openapi.session.DefaultOpenApiMachineSessionInvalidator;
import org.dromara.common.openapi.session.OpenApiMachineSessionBridge;
import org.dromara.common.openapi.session.OpenApiMachineSessionInvalidator;
import org.dromara.common.openapi.session.OpenApiMachineSessionOperations;
import org.dromara.common.openapi.session.SaTokenOpenApiMachineSessionOperations;
import org.dromara.common.openapi.spi.OpenApiAuthorizationResolver;
import org.dromara.common.openapi.spi.OpenApiCallEventPublisher;
import org.dromara.common.openapi.spi.OpenApiCredentialResolver;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Default-off assembly for the NAMEWTA OpenAPI gateway.
 */
@AutoConfiguration
@EnableConfigurationProperties(OpenApiProperties.class)
@ConditionalOnProperty(prefix = "openapi", name = "enabled", havingValue = "true")
public class OpenApiAutoConfiguration {

    @Bean
    OpenApiStartupValidator openApiStartupValidator(OpenApiProperties properties) {
        return new OpenApiStartupValidator(properties);
    }

    @Bean
    SpringDocOperationSchemaResolver openApiOperationSchemaResolver() {
        return new SpringDocOperationSchemaResolver();
    }

    @Bean
    OpenApiOperationRegistry openApiOperationRegistry(
        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
        SpringDocOperationSchemaResolver schemaResolver) {
        return new OpenApiOperationRegistry(handlerMapping, schemaResolver);
    }

    @Bean
    OpenApiCanonicalizer openApiCanonicalizer() {
        return new OpenApiCanonicalizer();
    }

    @Bean
    OpenApiSigner openApiSigner(OpenApiCanonicalizer canonicalizer) {
        return new OpenApiSigner(canonicalizer);
    }

    @Bean
    OpenApiAuthorizationMatcher openApiAuthorizationMatcher() {
        return new OpenApiAuthorizationMatcher();
    }

    @Bean
    OpenApiNonceStore openApiNonceStore(RedissonClient redissonClient) {
        return new RedissonOpenApiNonceStore(redissonClient);
    }

    @Bean
    OpenApiRateLimiter openApiRateLimiter(RedissonClient redissonClient) {
        return new RedissonOpenApiRateLimiter(redissonClient);
    }

    @Bean
    OpenApiMachineSessionOperations openApiMachineSessionOperations(StpLogic stpLogic,
                                                                    RedissonClient redissonClient) {
        return new SaTokenOpenApiMachineSessionOperations(stpLogic, redissonClient);
    }

    @Bean
    OpenApiMachineSessionBridge openApiMachineSessionBridge(OpenApiAuthorizationResolver authorizationResolver,
                                                            OpenApiMachineSessionOperations sessionOperations,
                                                            OpenApiProperties properties) {
        return new OpenApiMachineSessionBridge(authorizationResolver, sessionOperations,
            properties.getMachineSessionTtl());
    }

    @Bean
    OpenApiMachineSessionInvalidator openApiMachineSessionInvalidator(
        OpenApiMachineSessionOperations sessionOperations) {
        return new DefaultOpenApiMachineSessionInvalidator(sessionOperations);
    }

    @Bean
    @ConditionalOnMissingBean(OpenApiCallEventPublisher.class)
    OpenApiCallEventPublisher openApiCallEventPublisher() {
        return event -> { };
    }

    @Bean
    FilterRegistrationBean<OpenApiGatewayFilter> openApiGatewayFilterRegistration(
        @Qualifier("requestMappingHandlerMapping")
        RequestMappingHandlerMapping handlerMapping,
        OpenApiOperationRegistry operationRegistry,
        OpenApiCredentialResolver credentialResolver,
        OpenApiSigner signer,
        OpenApiNonceStore nonceStore,
        OpenApiRateLimiter rateLimiter,
        OpenApiMachineSessionBridge sessionBridge,
        OpenApiAuthorizationMatcher authorizationMatcher,
        OpenApiCallEventPublisher eventPublisher,
        OpenApiProperties properties) {
        OpenApiGatewayFilter filter = new OpenApiGatewayFilter(handlerMapping, operationRegistry,
            credentialResolver, signer, nonceStore, rateLimiter, sessionBridge, authorizationMatcher,
            eventPublisher, properties);
        FilterRegistrationBean<OpenApiGatewayFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("openApiGatewayFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setAsyncSupported(false);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
