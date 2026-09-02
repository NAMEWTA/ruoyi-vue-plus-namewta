package org.dromara.profile.person.service;

import org.dromara.workflow.api.event.ProcessEvent;

public interface PersonWorkflowGateway {

    void start(long applicationId, long submissionId, int snapshotVersion);

    default Integer persistedSnapshotVersion(ProcessEvent event) {
        return null;
    }
}
