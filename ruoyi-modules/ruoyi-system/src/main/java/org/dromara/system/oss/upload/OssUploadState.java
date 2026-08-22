package org.dromara.system.oss.upload;

/**
 * Redis 上传会话状态。
 */
public enum OssUploadState {
    INITIALIZED,
    UPLOADING,
    COMPLETING,
    COMPLETED,
    ABORTED,
    EXPIRED
}
