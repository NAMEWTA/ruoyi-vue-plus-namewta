package org.dromara.system.notify.attachment;

import org.dromara.common.notify.exception.NotifyAttachmentSnapshotException;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.OssObjectStat;
import org.dromara.system.domain.SysOss;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 基于 common-oss 服务端复制能力创建通知附件快照。
 */
@Component
public class DefaultNotifyAttachmentObjectStore implements NotifyAttachmentObjectStore {

    @Override
    public CopiedObject copy(SysOss source, long notifyLogId) {
        OssClient client = null;
        String objectKey = null;
        boolean copied = false;
        try {
            client = OssFactory.instance(source.getService());
            objectKey = client.buildPathKey("notify/" + notifyLogId, source.getOriginalName());
            client.copyObject(source.getFileName(), objectKey);
            copied = true;
            OssObjectStat stat = client.headObject(objectKey);
            return new CopiedObject(source.getService(), objectKey, stat.size(), stat.contentType());
        } catch (RuntimeException exception) {
            if (copied) {
                try {
                    client.delete(objectKey);
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new NotifyAttachmentSnapshotException("SNAPSHOT_COPY_FAILED", "通知附件 OSS 复制失败", exception);
        }
    }

    @Override
    public void materialize(CopiedObject object, Path target) {
        try {
            OssFactory.instance(object.service()).download(object.objectKey(), target);
        } catch (RuntimeException exception) {
            throw new NotifyAttachmentSnapshotException("SNAPSHOT_MATERIALIZE_FAILED",
                "通知附件 OSS 下载失败", exception);
        }
    }

    @Override
    public void delete(CopiedObject object) {
        try {
            if (!OssFactory.instance(object.service()).delete(object.objectKey())) {
                throw new NotifyAttachmentSnapshotException("SNAPSHOT_CLEANUP_FAILED", "通知附件 OSS 快照删除失败");
            }
        } catch (NotifyAttachmentSnapshotException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NotifyAttachmentSnapshotException("SNAPSHOT_CLEANUP_FAILED",
                "通知附件 OSS 快照删除失败", exception);
        }
    }
}
