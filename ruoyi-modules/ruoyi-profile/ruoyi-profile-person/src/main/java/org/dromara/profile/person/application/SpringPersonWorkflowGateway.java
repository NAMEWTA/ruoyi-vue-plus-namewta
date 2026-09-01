package org.dromara.profile.person.application;

import org.dromara.system.api.ConfigService;
import org.dromara.workflow.api.WorkflowService;
import org.dromara.workflow.api.domain.StartProcessDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SpringPersonWorkflowGateway implements PersonWorkflowGateway {

    static final String FLOW_CODE_KEY = "profile.person.flowCode";

    private final ObjectProvider<WorkflowService> workflowProvider;
    private final ConfigService configService;

    public SpringPersonWorkflowGateway(ObjectProvider<WorkflowService> workflowProvider,
                                       ConfigService configService) {
        this.workflowProvider = workflowProvider;
        this.configService = configService;
    }

    @Override
    public void start(long applicationId, long submissionId, int snapshotVersion) {
        WorkflowService workflow = workflowProvider.getIfAvailable();
        String flowCode = configService.getConfigValue(FLOW_CODE_KEY);
        if (workflow == null || flowCode == null || flowCode.isBlank()) {
            throw new PersonApplicationException("PERSON_WORKFLOW_UNAVAILABLE");
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("profileType", "PERSON");
        variables.put("snapshotVersion", snapshotVersion);
        variables.put("submissionId", submissionId);
        StartProcessDTO process = new StartProcessDTO();
        process.setBusinessId(String.valueOf(applicationId));
        process.setFlowCode(flowCode.strip());
        process.setVariables(variables);
        try {
            if (!workflow.startCompleteTask(process)) {
                throw new PersonApplicationException("PERSON_WORKFLOW_START_FAILED");
            }
        } catch (PersonApplicationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PersonApplicationException("PERSON_WORKFLOW_START_FAILED", exception);
        }
    }
}
