package org.dromara.system.oss.upload;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 只报告外部 Bucket 前置条件，不读取 Secret、不修改 Bucket policy。
 */
@Slf4j
@Component
public class OssUploadDiagnostics {

    public List<String> requirements() {
        return List.of(
            "Bucket CORS 必须只允许明确的前端 Origin 和 PUT 方法",
            "Bucket CORS 必须向浏览器暴露 ETag 响应头",
            "Bucket Lifecycle 应配置 AbortIncompleteMultipartUpload 作为应用清理兜底"
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        try {
            var result = OssFactory.instance().bucketConfiguration();
            if (result.compliant()) {
                log.info("OSS Direct Upload Bucket [{}] 前置配置检查通过", result.bucket());
                return;
            }
            if (!result.explicitCorsOrigins()) {
                log.warn("OSS Direct Upload Bucket [{}] CORS 未配置明确 Origin", result.bucket());
            }
            if (!result.corsAllowsPut()) {
                log.warn("OSS Direct Upload Bucket [{}] CORS 未允许 PUT", result.bucket());
            }
            if (!result.corsExposesEtag()) {
                log.warn("OSS Direct Upload Bucket [{}] CORS 未暴露 ETag", result.bucket());
            }
            if (!result.abortIncompleteMultipartUpload()) {
                log.warn("OSS Direct Upload Bucket [{}] 未配置 AbortIncompleteMultipartUpload", result.bucket());
            }
            result.issues().forEach(issue -> log.warn("OSS Direct Upload Bucket [{}]: {}", result.bucket(), issue));
        } catch (RuntimeException ex) {
            log.error("OSS Direct Upload 默认 Bucket 前置配置检查失败: {}", ex.getMessage());
        }
    }
}
