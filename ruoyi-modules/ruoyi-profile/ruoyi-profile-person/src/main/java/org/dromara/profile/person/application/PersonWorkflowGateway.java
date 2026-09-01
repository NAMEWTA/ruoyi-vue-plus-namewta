package org.dromara.profile.person.application;

public interface PersonWorkflowGateway {

    void start(long applicationId, long submissionId, int snapshotVersion);
}
