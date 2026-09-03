package org.dromara.profile.person.listener;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import lombok.RequiredArgsConstructor;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.service.PersonWorkflowGateway;
import org.dromara.profile.person.usecase.PersonRebindUseCase;
import org.dromara.profile.person.service.impl.PersonRebindNotificationService;
import org.dromara.system.api.ConfigService;
import org.dromara.workflow.api.event.ProcessEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

/**
 * PersonRebindProcessListener Profile 业务组件。
 */
@Service
@RequiredArgsConstructor
public class PersonRebindProcessListener {

    private static final String FLOW_CODE_KEY = "profile.person.flowCode";

    private final PersonRebindUseCase useCase;
    private final ProfileMaterialPort materials;
    private final PersonWorkflowGateway workflow;
    private final ConfigService configService;
    private final PersonRebindNotificationService notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock = Clock.systemUTC();

    /**
     * 处理 handle 业务步骤。
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @DSTransactional
    public void handle(ProcessEvent event) {
        if (event == null || !expectedFlowCode().equals(event.getFlowCode())
            || !"FINISH".equals(normalizeStatus(event.getStatus()))) {
            return;
        }
        if ("REJECT".equals(normalizeDecision(event.getParams()))) {
            return;
        }
        Long applicationId = positiveLong(event.getBusinessId());
        if (applicationId == null) {
            return;
        }
        Integer snapshotVersion = snapshotVersion(event.getParams());
        if (snapshotVersion == null) {
            snapshotVersion = workflow.persistedSnapshotVersion(event);
        }
        if (snapshotVersion == null) {
            return;
        }
        useCase.publishApproved(applicationId, snapshotVersion, clock.instant()).ifPresent(publication -> {
            materials.snapshotImmutable(owner(MaterialOwnerType.SUBMISSION, publication.personSubmissionId()),
                owner(MaterialOwnerType.VERSION, publication.personVersionId()));
            notifications.stage(publication.event());
            events.publishEvent(publication.event());
        });
    }

    /**
     * 处理 expectedFlowCode 业务步骤。
     */
    private String expectedFlowCode() {
        String flowCode = configService.getConfigValue(FLOW_CODE_KEY);
        return flowCode == null ? "" : flowCode.strip();
    }

    /**
     * 处理 owner 业务步骤。
     */
    private MaterialOwnerKey owner(MaterialOwnerType type, long ownerId) {
        return new MaterialOwnerKey(ProfileType.PERSON, type, ownerId);
    }

    /**
     * 处理 snapshotVersion 业务步骤。
     */
    private Integer snapshotVersion(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        Object value = params.get("snapshotVersion");
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 处理 positiveLong 业务步骤。
     */
    private Long positiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 处理 normalizeStatus 业务步骤。
     */
    private String normalizeStatus(String status) {
        return status == null ? "" : status.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * 处理 normalizeDecision 业务步骤。
     */
    private String normalizeDecision(Map<String, Object> params) {
        Object value = params == null ? null : params.get("profileDecision");
        return value == null ? "" : value.toString().strip().toUpperCase(Locale.ROOT);
    }
}
