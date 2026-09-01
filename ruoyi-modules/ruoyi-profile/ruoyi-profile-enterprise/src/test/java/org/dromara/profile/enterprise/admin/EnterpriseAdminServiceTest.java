package org.dromara.profile.enterprise.admin;

import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.ProfileService;
import org.dromara.profile.enterprise.application.EnterpriseApplicationRepository;
import org.dromara.profile.enterprise.application.EnterprisePublication;
import org.dromara.profile.enterprise.application.EnterpriseSubmission;
import org.dromara.system.api.UserService;
import org.dromara.workflow.api.WorkflowService;
import org.dromara.workflow.api.domain.WorkflowTerminationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("dev")
class EnterpriseAdminServiceTest {

    private final EnterpriseAdminRepository repository = mock(EnterpriseAdminRepository.class);
    private final EnterpriseApplicationRepository applications = mock(EnterpriseApplicationRepository.class);
    private final ProfileMaterialPort materials = mock(ProfileMaterialPort.class);
    private final WorkflowService workflow = mock(WorkflowService.class);
    private final UserService users = mock(UserService.class);
    private final ProfileService profiles = mock(ProfileService.class);
    private final EnterpriseAdminService service = new EnterpriseAdminService(repository, applications, materials,
        workflow, users, profiles, Clock.fixed(Instant.parse("2026-09-02T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void approvalTerminatesBeforePublishingAndFinalizesAdminDecision() {
        var state = new EnterpriseAdminRepository.DecisionState(11L, 22L, 3, 4);
        when(repository.beginDecision(11L, "APPROVED", 99L, "checked", Instant.parse("2026-09-02T00:00:00Z")))
            .thenReturn(state);
        when(workflow.terminateInstance("11", "checked"))
            .thenReturn(new WorkflowTerminationResult(WorkflowTerminationResult.Status.TERMINATED, 7L));
        when(applications.requireSubmission(11L, 3)).thenReturn(new EnterpriseSubmission(22L, 11L, 3,
            77L, null, "manual", Instant.parse("2026-09-01T00:00:00Z")));
        when(applications.publishApproved(11L, 3, Instant.parse("2026-09-02T00:00:00Z")))
            .thenReturn(new EnterprisePublication(31L, 41L, 51L, false));

        service.decide(99L, 11L, new EnterpriseAdminContracts.DecisionCommand("APPROVED", "checked"));

        var order = inOrder(repository, workflow, applications, materials);
        order.verify(repository).beginDecision(11L, "APPROVED", 99L, "checked",
            Instant.parse("2026-09-02T00:00:00Z"));
        order.verify(workflow).terminateInstance("11", "checked");
        order.verify(repository).resumeForApproval(state, 99L);
        order.verify(applications).publishApproved(11L, 3, Instant.parse("2026-09-02T00:00:00Z"));
        order.verify(materials).snapshotImmutable(any(), any());
        order.verify(repository).finalizeApproved(state, 31L, 41L, 99L, "checked",
            Instant.parse("2026-09-02T00:00:00Z"));
    }

    @Test
    void terminationFailureNeverPublishesOrFinalizes() {
        var state = new EnterpriseAdminRepository.DecisionState(11L, 22L, 3, 4);
        when(repository.beginDecision(anyLong(), anyString(), anyLong(), anyString(), any())).thenReturn(state);
        when(workflow.terminateInstance("11", "checked")).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service.decide(99L, 11L,
            new EnterpriseAdminContracts.DecisionCommand("APPROVED", "checked")))
            .isInstanceOf(EnterpriseAdminException.class).hasMessage("ENTERPRISE_ADMIN_WORKFLOW_TERMINATION_FAILED");
        verify(applications, never()).publishApproved(anyLong(), anyInt(), any());
        verify(repository, never()).finalizeApproved(any(), anyLong(), anyLong(), anyLong(), anyString(), any());
    }
}
