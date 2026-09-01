package org.dromara.profile.enterprise.application;

import org.dromara.workflow.api.event.ProcessEvent;

public interface EnterpriseWorkflowGateway {

    void start(long applicationId, long submissionId, int snapshotVersion);

    default Integer persistedSnapshotVersion(ProcessEvent event) {
        return null;
    }
}
