package org.dromara.system.oss.exception;

/**
 * OSS 生命周期稳定错误分类。
 */
public enum OssLifecycleError {
    OBJECT_NOT_FOUND,
    OBJECT_REFERENCED,
    OBJECT_DELETE_PENDING,
    INVALID_REFERENCE,
    PROVIDER_DELETE_FAILED
}
