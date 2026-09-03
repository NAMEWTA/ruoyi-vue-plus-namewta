package org.dromara.profile.enterprise.service;

import org.dromara.workflow.api.event.ProcessEvent;

/** 企业工作流网关，封装流程启动和事件变量读取。 */
public interface EnterpriseWorkflowGateway {

    /** 启动企业档案审批流程。 */
    void start(long applicationId, long submissionId, int snapshotVersion);

    /** 读取流程事件中的持久化快照版本。 */
    default Integer persistedSnapshotVersion(ProcessEvent event) {
        return null;
    }
}
