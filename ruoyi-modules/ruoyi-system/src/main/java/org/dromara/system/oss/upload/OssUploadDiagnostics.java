package org.dromara.system.oss.upload;

import lombok.extern.slf4j.Slf4j;
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
        requirements().forEach(requirement -> log.warn("OSS Direct Upload 运维前置检查: {}", requirement));
    }
}
