package org.dromara.test.oss.client;

import org.dromara.common.oss.client.DefaultOssClientImpl;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.config.OssAsyncExecutorConfig;
import org.dromara.common.oss.config.OssClientConfig;
import org.dromara.common.oss.model.OssCompletedPart;
import org.dromara.common.oss.model.OssObjectOptions;
import org.dromara.common.oss.model.OssPresignedRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MinIO 上的浏览器直传、分片、下载和清理协议验证。
 */
@Tag("dev")
class MinioOssClientIntegrationTest {

    @Test
    void roundTripsPresignedSingleAndMultipartTransfers() throws Exception {
        String endpoint = System.getProperty("oss.minio.integration.endpoint");
        Assumptions.assumeTrue(endpoint != null && !endpoint.isBlank(), "需要一次性 MinIO endpoint");
        String accessKey = System.getProperty("oss.minio.integration.access-key", "namewta");
        String secretKey = System.getProperty("oss.minio.integration.secret-key", "namewta123");
        URI endpointUri = URI.create(endpoint);
        String bucket = "namewta-ci-" + UUID.randomUUID().toString().replace("-", "");
        String singleKey = "direct/single.txt";
        String multipartKey = "direct/multipart.bin";
        byte[] singleBody = "namewta-minio-single".getBytes(StandardCharsets.UTF_8);
        byte[] multipartBody = "namewta-minio-multipart".getBytes(StandardCharsets.UTF_8);

        try (S3Client bootstrap = bootstrap(endpointUri, accessKey, secretKey)) {
            bootstrap.createBucket(builder -> builder.bucket(bucket));
            try (OssClient client = new DefaultOssClientImpl(
                "minio-integration", clientConfig(endpointUri, accessKey, secretKey, bucket))) {
                HttpClient http = HttpClient.newHttpClient();

                OssPresignedRequest singlePut = client.presignPut(singleKey, Duration.ofMinutes(5),
                    new OssObjectOptions("text/plain", Map.of("test-owner", "namewta-ci"), null));
                assertEquals(200, executePut(http, singlePut, singleBody).statusCode());
                assertEquals(singleBody.length, client.headObject(singleKey).size());

                HttpResponse<byte[]> singleGet = http.send(
                    HttpRequest.newBuilder(URI.create(client.presignGet(singleKey, Duration.ofMinutes(5)).url()))
                        .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, singleGet.statusCode());
                assertEquals("namewta-minio-single", new String(singleGet.body(), StandardCharsets.UTF_8));

                String uploadId = client.createMultipartUpload(multipartKey, OssObjectOptions.empty()).uploadId();
                HttpResponse<byte[]> partResponse = executePut(http,
                    client.presignUploadPart(multipartKey, uploadId, 1, Duration.ofMinutes(5)), multipartBody);
                assertEquals(200, partResponse.statusCode());
                String eTag = partResponse.headers().firstValue("etag").orElseThrow();
                assertEquals(multipartBody.length, client.listParts(multipartKey, uploadId).getFirst().size());
                client.completeMultipartUpload(multipartKey, uploadId,
                    java.util.List.of(new OssCompletedPart(1, eTag, Map.of())));
                assertEquals(multipartBody.length, client.headObject(multipartKey).size());

                String abandoned = client.createMultipartUpload("direct/abandoned.bin", OssObjectOptions.empty())
                    .uploadId();
                assertTrue(client.abortMultipartUpload("direct/abandoned.bin", abandoned));
            } finally {
                bootstrap.deleteObject(builder -> builder.bucket(bucket).key(singleKey));
                bootstrap.deleteObject(builder -> builder.bucket(bucket).key(multipartKey));
                bootstrap.deleteBucket(builder -> builder.bucket(bucket));
            }
        }
    }

    private HttpResponse<byte[]> executePut(HttpClient http, OssPresignedRequest request, byte[] body)
        throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        request.requiredHeaders().forEach(builder::header);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private S3Client bootstrap(URI endpoint, String accessKey, String secretKey) {
        return S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    }

    private OssClientConfig clientConfig(URI endpoint, String accessKey, String secretKey, String bucket) {
        return OssClientConfig.builder()
            .endpoint(endpoint.getAuthority())
            .useHttps("https".equalsIgnoreCase(endpoint.getScheme()))
            .usePathStyleAccess(true)
            .accessKey(accessKey)
            .secretKey(secretKey)
            .bucket(bucket)
            .region(Region.US_EAST_1)
            .prefix("")
            .asyncExecutorConfig(OssAsyncExecutorConfig.DEFAULT)
            .build();
    }
}
