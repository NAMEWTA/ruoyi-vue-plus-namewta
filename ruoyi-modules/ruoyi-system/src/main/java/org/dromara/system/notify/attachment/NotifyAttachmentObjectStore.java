package org.dromara.system.notify.attachment;

import org.dromara.system.domain.SysOss;

import java.nio.file.Path;

/**
 * 通知附件快照使用的 system 内部对象存储接缝。
 */
public interface NotifyAttachmentObjectStore {

    CopiedObject copy(SysOss source, long notifyLogId);

    void materialize(CopiedObject object, Path target);

    void delete(CopiedObject object);

    /**
     * 已复制对象的稳定定位信息。
     */
    record CopiedObject(String service, String objectKey, long size, String contentType) {
    }
}
