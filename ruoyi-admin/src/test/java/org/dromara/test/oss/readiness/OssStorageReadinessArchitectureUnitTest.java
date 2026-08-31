package org.dromara.test.oss.readiness;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class OssStorageReadinessArchitectureUnitTest {

    @Test
    void productionReadinessDoesNotMutateProviderOrExposeAnonymousEndpoints() throws Exception {
        Path root = repositoryRoot();
        String common = Files.readString(root.resolve(
            "ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/client/AbstractOssClientImpl.java"));
        String readiness = Files.readString(root.resolve(
            "ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/oss/readiness/OssStorageReadinessService.java"));
        String health = Files.readString(root.resolve(
            "ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/oss/readiness/OssStorageReadinessHealthIndicator.java"));

        assertThat(common)
            .contains("getBucketPolicy", "getBucketAcl", "HttpRequest.BodyPublishers.noBody()")
            .doesNotContain("putBucketPolicy", "deleteBucketPolicy", "createBucket(", "putObject(");
        assertThat(readiness).doesNotContain("accessKey", "secretKey", "getEndpoint");
        assertThat(health).doesNotContain("endpoint", "domainUrl", "secretKey", "accessKey", "policyText");
    }

    private Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        return current.getFileName().toString().equals("ruoyi-admin") ? current.getParent() : current;
    }
}
