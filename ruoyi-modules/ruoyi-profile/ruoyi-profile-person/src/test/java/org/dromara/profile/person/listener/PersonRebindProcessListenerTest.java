package org.dromara.profile.person.listener;

import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.event.PersonReboundEvent;
import org.dromara.profile.person.domain.application.PersonRebindPublication;
import org.dromara.profile.person.usecase.PersonRebindUseCase;
import org.dromara.profile.person.service.PersonWorkflowGateway;
import org.dromara.profile.person.service.impl.PersonRebindNotificationService;
import org.dromara.system.api.ConfigService;
import org.dromara.workflow.api.event.ProcessEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PersonRebindProcessListenerTest {

    private static final String FLOW_CODE = "person-profile";

    private final PersonRebindUseCase service = mock(PersonRebindUseCase.class);
    private final ProfileMaterialPort materials = mock(ProfileMaterialPort.class);
    private final PersonWorkflowGateway workflow = mock(PersonWorkflowGateway.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final PersonRebindNotificationService notifications = mock(PersonRebindNotificationService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PersonRebindProcessListener listener = new PersonRebindProcessListener(
        service, materials, workflow, configService, notifications, events);

    @Test
    void approvedRebindPublishesFromTheFrozenSubmission() {
        PersonReboundEvent rebound = new PersonReboundEvent(9201L, 9001L, 202L);
        PersonRebindPublication publication = new PersonRebindPublication(9101L, 9402L, rebound);
        when(configService.getConfigValue("profile.person.flowCode")).thenReturn(FLOW_CODE);
        when(service.publishApproved(anyLong(), anyInt(), any(Instant.class)))
            .thenReturn(Optional.of(publication));

        listener.handle(event("FINISH", Map.of("snapshotVersion", 1, "profileDecision", "APPROVE")));

        verify(service).publishApproved(anyLong(), anyInt(), any(Instant.class));
        verify(materials).snapshotImmutable(
            new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.SUBMISSION, 9101L),
            new MaterialOwnerKey(ProfileType.PERSON, MaterialOwnerType.VERSION, 9402L));
        verify(notifications).stage(rebound);
        verify(events).publishEvent(rebound);
    }

    @Test
    void rejectedFinishLeavesRejectionToTheOrdinaryApplicationListener() {
        when(configService.getConfigValue("profile.person.flowCode")).thenReturn(FLOW_CODE);

        listener.handle(event(" finish ", Map.of("snapshotVersion", 1, "profileDecision", " reject ")));

        verify(service, never()).publishApproved(anyLong(), anyInt(), any(Instant.class));
        verifyNoPublicationSideEffects();
    }

    @Test
    void nonFinishEventDoesNotPublishARebind() {
        when(configService.getConfigValue("profile.person.flowCode")).thenReturn(FLOW_CODE);

        listener.handle(event("BACK", Map.of("snapshotVersion", 1, "profileDecision", "APPROVE")));

        verify(service, never()).publishApproved(anyLong(), anyInt(), any(Instant.class));
        verifyNoPublicationSideEffects();
    }

    @Test
    void ordinaryApplicationDoesNotProduceRebindSideEffects() {
        when(configService.getConfigValue("profile.person.flowCode")).thenReturn(FLOW_CODE);
        when(service.publishApproved(anyLong(), anyInt(), any(Instant.class))).thenReturn(Optional.empty());

        listener.handle(event("FINISH", Map.of("snapshotVersion", 1, "profileDecision", "APPROVE")));

        verify(service).publishApproved(anyLong(), anyInt(), any(Instant.class));
        verifyNoPublicationSideEffects();
    }

    private ProcessEvent event(String status, Map<String, Object> params) {
        ProcessEvent event = new ProcessEvent();
        event.setFlowCode(FLOW_CODE);
        event.setBusinessId("9001");
        event.setStatus(status);
        event.setParams(params);
        return event;
    }

    private void verifyNoPublicationSideEffects() {
        verify(materials, never()).snapshotImmutable(any(), any());
        verify(notifications, never()).stage(any());
        verify(events, never()).publishEvent(any());
    }
}
