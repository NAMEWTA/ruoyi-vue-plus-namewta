package org.dromara.system.oss.migration;

public enum OssMigrationStage {
    PREFLIGHT,
    COPIED,
    CONTENT_VERIFIED,
    SERVICE_SWITCHED,
    ACCESS_VERIFIED,
    CLEANUP_ELIGIBLE,
    COMPLETED,
    ROLLED_BACK
}
