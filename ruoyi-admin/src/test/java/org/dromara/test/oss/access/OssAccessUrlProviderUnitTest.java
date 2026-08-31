package org.dromara.test.oss.access;

import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.config.OssAsyncExecutorConfig;
import org.dromara.common.oss.config.OssClientConfig;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.system.domain.SysOss;
import org.dromara.system.oss.provider.DefaultOssObjectStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import software.amazon.awssdk.regions.Region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("dev")
class OssAccessUrlProviderUnitTest {

    @Test
    void publicUrlUsesBucketBoundDomainAndStructurallyEncodesObjectKey() {
        OssClient client = mock(OssClient.class);
        when(client.config()).thenReturn(OssClientConfig.builder()
            .endpoint("storage.internal.test")
            .domain("cdn.example.test/assets")
            .useHttps(true)
            .usePathStyleAccess(true)
            .bucket("public-bucket")
            .region(Region.US_EAST_1)
            .prefix("")
            .asyncExecutorConfig(OssAsyncExecutorConfig.DEFAULT)
            .build());
        SysOss oss = new SysOss();
        oss.setService("public");
        oss.setFileName("documents/a b+#.txt");

        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(() -> OssFactory.instance("public")).thenReturn(client);

            assertThat(new DefaultOssObjectStore().publicUrl(oss))
                .isEqualTo("https://cdn.example.test/assets/documents/a%20b%2B%23.txt");
        }
    }
}
