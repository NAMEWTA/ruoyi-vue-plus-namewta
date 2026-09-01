package org.dromara.profile.person.application;

import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.person.verification.PersonVerificationAttemptCoordinator;
import org.dromara.profile.person.verification.PersonVerificationException;
import org.dromara.profile.person.verification.PersonVerificationFailureCategory;
import org.dromara.profile.person.verification.PersonVerificationProviderRegistry;
import org.dromara.system.api.ConfigService;
import org.dromara.workflow.api.event.ProcessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PersonApplicationServiceTest {

    private final PersonApplicationRepository repository = mock(PersonApplicationRepository.class);
    private final ProfileMaterialPort materials = mock(ProfileMaterialPort.class);
    private final PersonVerificationProviderRegistry providers = mock(PersonVerificationProviderRegistry.class);
    private final PersonVerificationAttemptCoordinator attempts = mock(PersonVerificationAttemptCoordinator.class);
    private final PersonWorkflowGateway workflow = mock(PersonWorkflowGateway.class);
    private final ConfigService config = mock(ConfigService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);

    private PersonApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PersonApplicationService(repository, materials, providers, attempts, workflow, config, clock);
    }

    @Test
    void savesCurrentUsersDraftWithFixedEnabledProvider() {
        when(config.getConfigValue("profile.person.provider.default")).thenReturn("manual");
        when(repository.findOpenByUserId(101L)).thenReturn(Optional.empty());
        when(repository.findDocumentType("CN_RESIDENT_ID")).thenReturn(Optional.of(documentType()));
        when(repository.saveDraft(eq(101L), eq("manual"), any())).thenAnswer(invocation ->
            application(9001L, 101L, "DRAFT", 0, 0));

        PersonApplicationView result = service.save(101L, command(0));

        assertThat(result.personApplicationId()).isEqualTo(9001L);
        assertThat(result.providerCode()).isEqualTo("manual");
        verify(providers).requireEnabled("manual");
    }

    @Test
    void keepsTheCurrentAccountsProfileAsTheReauthenticationTargetWhenIdentityChanges() {
        when(config.getConfigValue("profile.person.provider.default")).thenReturn("manual");
        when(repository.findOpenByUserId(101L)).thenReturn(Optional.empty());
        when(repository.findDocumentType("CN_RESIDENT_ID")).thenReturn(Optional.of(documentType()));
        when(repository.findEffectiveProfileIdByUser(101L)).thenReturn(9201L);
        when(repository.saveDraft(eq(101L), eq("manual"), any())).thenAnswer(invocation ->
            application(9001L, 101L, "DRAFT", 0, 0));

        service.save(101L, command(0));

        org.mockito.ArgumentCaptor<PersonDraftUpdate> update =
            org.mockito.ArgumentCaptor.forClass(PersonDraftUpdate.class);
        verify(repository).saveDraft(eq(101L), eq("manual"), update.capture());
        assertThat(update.getValue().targetProfileId()).isEqualTo(9201L);
    }

    @Test
    void mapsProviderAvailabilityFailuresToTheApplicationContract() {
        when(config.getConfigValue("profile.person.provider.default")).thenReturn("manual");
        when(repository.findOpenByUserId(101L)).thenReturn(Optional.empty());
        when(repository.findDocumentType("CN_RESIDENT_ID")).thenReturn(Optional.of(documentType()));
        doThrow(new PersonVerificationException(PersonVerificationFailureCategory.DISABLED_PROVIDER,
            "provider disabled")).when(providers).requireEnabled("manual");

        assertThatThrownBy(() -> service.save(101L, command(0)))
            .isInstanceOf(PersonApplicationException.class)
            .hasMessage("PERSON_PROVIDER_UNAVAILABLE");
        verify(repository, never()).saveDraft(anyLong(), any(), any());
    }

    @Test
    void submitsImmutableFieldAndMaterialSnapshotBeforeWorkflow() {
        PersonApplication application = application(9001L, 101L, "DRAFT", 0, 0);
        PersonSubmission submission = new PersonSubmission(9101L, 9001L, 1, 101L,
            application.fields(), "manual", Instant.parse("2026-09-01T12:00:00Z"));
        when(repository.lockOpenByUserId(101L)).thenReturn(application);
        when(repository.findDocumentType("CN_RESIDENT_ID")).thenReturn(Optional.of(documentType()));
        when(repository.insertSubmission(eq(application), any(), eq(1), any())).thenReturn(submission);
        when(repository.markWaiting(9001L, 1, 0, submission.submittedTime()))
            .thenReturn(application(9001L, 101L, "WAITING", 1, 1));

        PersonApplicationView result = service.submit(101L, 0);

        assertThat(result.status()).isEqualTo("WAITING");
        verify(repository).requireSubmissionAllowed(101L, application.targetProfileId(),
            application.fields().identityKey());
        verify(materials).validateRequired(new ProfileMaterialPort.MaterialOwnerKey(
            ProfileType.PERSON, ProfileMaterialPort.MaterialOwnerType.WORKING, 9001L),
            "CN_RESIDENT_ID", Set.of("ALWAYS"));
        verify(materials).snapshotImmutable(new ProfileMaterialPort.MaterialOwnerKey(
            ProfileType.PERSON, ProfileMaterialPort.MaterialOwnerType.WORKING, 9001L),
            new ProfileMaterialPort.MaterialOwnerKey(
                ProfileType.PERSON, ProfileMaterialPort.MaterialOwnerType.SUBMISSION, 9101L));
        verify(workflow).start(9001L, 9101L, 1);
    }

    @Test
    void workflowFailureDoesNotPublishAnything() {
        PersonApplication application = application(9001L, 101L, "DRAFT", 0, 0);
        PersonSubmission submission = new PersonSubmission(9101L, 9001L, 1, 101L,
            application.fields(), "manual", Instant.parse("2026-09-01T12:00:00Z"));
        when(repository.lockOpenByUserId(101L)).thenReturn(application);
        when(repository.findDocumentType("CN_RESIDENT_ID")).thenReturn(Optional.of(documentType()));
        when(repository.insertSubmission(eq(application), any(), eq(1), any())).thenReturn(submission);
        when(repository.markWaiting(anyLong(), anyInt(), anyInt(), any()))
            .thenReturn(application(9001L, 101L, "WAITING", 1, 1));
        org.mockito.Mockito.doThrow(new PersonApplicationException("PERSON_WORKFLOW_UNAVAILABLE"))
            .when(workflow).start(9001L, 9101L, 1);

        assertThatThrownBy(() -> service.submit(101L, 0))
            .hasMessage("PERSON_WORKFLOW_UNAVAILABLE");
        verify(repository, never()).publishApproved(anyLong(), anyInt(), any());
    }

    @Test
    void finishesOnlyCurrentSnapshotAndIgnoresLateEvents() {
        PersonApplication waiting = application(9001L, 101L, "WAITING", 2, 3);
        when(config.getConfigValue("profile.person.flowCode")).thenReturn("profile_person_verification");
        when(repository.lockById(9001L)).thenReturn(waiting);
        when(repository.requireSubmission(9001L, 3)).thenReturn(new PersonSubmission(
            9101L, 9001L, 3, 101L, waiting.fields(), "manual",
            Instant.parse("2026-09-01T11:00:00Z")));
        when(repository.publishApproved(9001L, 3, Instant.parse("2026-09-01T12:00:00Z")))
            .thenReturn(new PersonPublication(9201L, 9301L, 9401L, false));

        service.handleProcessEvent(event("finish", 3));
        service.handleProcessEvent(event("back", 2));

        verify(repository).publishApproved(9001L, 3, Instant.parse("2026-09-01T12:00:00Z"));
        verify(repository, never()).updateWorkflowStatus(eq(9001L), eq(2), eq("BACK"), eq(2), any());
    }

    @Test
    void resolvesARealWorkflowTerminalFromThePersistedSnapshotFence() {
        PersonApplication waiting = application(9001L, 101L, "WAITING", 2, 3);
        when(config.getConfigValue("profile.person.flowCode")).thenReturn("profile_person_verification");
        when(repository.lockById(9001L)).thenReturn(waiting);
        when(workflow.persistedSnapshotVersion(any())).thenReturn(3);
        when(repository.requireSubmission(9001L, 3)).thenReturn(new PersonSubmission(
            9101L, 9001L, 3, 101L, waiting.fields(), "manual",
            Instant.parse("2026-09-01T11:00:00Z")));
        when(repository.publishApproved(9001L, 3, Instant.parse("2026-09-01T12:00:00Z")))
            .thenReturn(new PersonPublication(9201L, 9301L, 9401L, false));
        ProcessEvent event = event("finish", 3);
        event.setInstanceId(77L);
        event.setParams(Map.of("message", "approved"));

        service.handleProcessEvent(event);

        verify(repository).publishApproved(9001L, 3, Instant.parse("2026-09-01T12:00:00Z"));
    }

    @Test
    void ignoresLateFinishAfterTheSnapshotHasAlreadyReturnedToBack() {
        when(config.getConfigValue("profile.person.flowCode")).thenReturn("profile_person_verification");
        when(repository.lockById(9001L)).thenReturn(application(9001L, 101L, "BACK", 3, 3));

        service.handleProcessEvent(event("finish", 3));

        verify(repository, never()).requireSubmission(anyLong(), anyInt());
        verify(repository, never()).publishApproved(anyLong(), anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BACK", "CANCEL", "INVALID", "TERMINATION"})
    void recordsEveryNonApprovalWorkflowTerminalWithoutPublishing(String status) {
        when(config.getConfigValue("profile.person.flowCode")).thenReturn("profile_person_verification");
        when(repository.lockById(9001L)).thenReturn(application(9001L, 101L, "WAITING", 2, 1));

        service.handleProcessEvent(event(status.toLowerCase(java.util.Locale.ROOT), 1));

        verify(repository).updateWorkflowStatus(9001L, 1, status, 2,
            Instant.parse("2026-09-01T12:00:00Z"));
        verify(repository, never()).publishApproved(anyLong(), anyInt(), any());
    }

    @Test
    void workflowReviewRejectInvalidatesTheSnapshotWithoutPublishing() {
        when(config.getConfigValue("profile.person.flowCode")).thenReturn("profile_person_verification");
        when(repository.lockById(9001L)).thenReturn(application(9001L, 101L, "WAITING", 2, 1));
        ProcessEvent rejected = event("finish", 1);
        rejected.setParams(Map.of("snapshotVersion", 1, "profileDecision", "REJECT"));

        service.handleProcessEvent(rejected);

        verify(repository).updateWorkflowStatus(9001L, 1, "INVALID", 2,
            Instant.parse("2026-09-01T12:00:00Z"));
        verify(repository, never()).publishApproved(anyLong(), anyInt(), any());
    }

    @Test
    void rejectsInvalidDocumentAndValidityMatrix() {
        when(config.getConfigValue("profile.person.provider.default")).thenReturn("manual");
        when(repository.findOpenByUserId(101L)).thenReturn(Optional.empty());
        when(repository.findDocumentType("CN_RESIDENT_ID")).thenReturn(Optional.of(documentType()));
        PersonDraftCommand invalid = new PersonDraftCommand("A", "CN_RESIDENT_ID", "123",
            "MALE", LocalDate.of(1990, 1, 1), LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31), 0);

        assertThatThrownBy(() -> service.save(101L, invalid))
            .hasMessage("PERSON_DOCUMENT_NUMBER_INVALID");
        verify(repository, never()).saveDraft(anyLong(), any(), any());
    }

    private PersonDraftCommand command(int version) {
        return new PersonDraftCommand("张三", "CN_RESIDENT_ID", "110101199001011234",
            "MALE", LocalDate.of(1990, 1, 1), LocalDate.of(2020, 1, 1),
            LocalDate.of(2030, 1, 1), version);
    }

    private PersonApplication application(long applicationId, long userId, String status,
                                          int version, int submissionSeq) {
        PersonIdentityFields fields = PersonIdentityFields.normalize(command(version));
        return new PersonApplication(applicationId, userId, null, status, fields, "manual",
            submissionSeq, false, null, null, 0, version, null, null);
    }

    private DocumentTypeRule documentType() {
        return new DocumentTypeRule("CN_RESIDENT_ID", "^[0-9]{17}[0-9Xx]$", true);
    }

    private ProcessEvent event(String status, int snapshotVersion) {
        ProcessEvent event = new ProcessEvent();
        event.setFlowCode("profile_person_verification");
        event.setBusinessId("9001");
        event.setStatus(status);
        event.setParams(Map.of("snapshotVersion", snapshotVersion));
        event.setSubmit(false);
        return event;
    }
}
