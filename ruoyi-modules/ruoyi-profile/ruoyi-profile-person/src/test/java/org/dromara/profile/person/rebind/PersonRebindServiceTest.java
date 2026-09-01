package org.dromara.profile.person.rebind;

import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.person.application.DocumentTypeRule;
import org.dromara.profile.person.application.PersonApplication;
import org.dromara.profile.person.application.PersonApplicationRepository;
import org.dromara.profile.person.application.PersonDraftCommand;
import org.dromara.profile.person.application.PersonIdentityFields;
import org.dromara.profile.person.application.PersonSubmission;
import org.dromara.profile.person.application.PersonWorkflowGateway;
import org.dromara.profile.person.persistence.row.PersonApplicationRow;
import org.dromara.profile.person.rebind.PersonRebindContracts.ConfirmCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.IdentityCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.MatchCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.SubmitCommand;
import org.dromara.profile.person.verification.PersonVerificationAttemptCoordinator;
import org.dromara.profile.person.verification.PersonVerificationProviderRegistry;
import org.dromara.system.api.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class PersonRebindServiceTest {

    private final PersonRebindRepository rebinds = mock(PersonRebindRepository.class);
    private final PersonApplicationRepository applications = mock(PersonApplicationRepository.class);
    private final ProfileMaterialPort materials = mock(ProfileMaterialPort.class);
    private final PersonVerificationProviderRegistry providers = mock(PersonVerificationProviderRegistry.class);
    private final PersonVerificationAttemptCoordinator attempts = mock(PersonVerificationAttemptCoordinator.class);
    private final PersonWorkflowGateway workflow = mock(PersonWorkflowGateway.class);
    private final UserService users = mock(UserService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);

    private PersonRebindService service;

    @BeforeEach
    void setUp() {
        service = new PersonRebindService(rebinds, applications, materials, providers, attempts, workflow,
            users, clock);
        when(applications.findDocumentType("CN_RESIDENT_ID"))
            .thenReturn(Optional.of(new DocumentTypeRule("CN_RESIDENT_ID", "^[0-9]{17}[0-9Xx]$", true)));
        when(applications.findEffectiveProfileIdByUser(101L)).thenReturn(null);
    }

    @Test
    void revealsMaskedPhoneOnlyAfterAnExactCompleteMatch() {
        PersonApplication application = application(false, 0, "DRAFT");
        PersonRebindMapper.RebindCandidateRow candidate = candidate();
        when(applications.findOpenByUserId(101L)).thenReturn(Optional.of(application));
        when(rebinds.findExactCandidate(application.fields())).thenReturn(Optional.of(candidate));
        when(users.selectPhonenumberById(202L)).thenReturn("13800138000");

        var matched = service.match(101L, new MatchCommand(identity()));
        var wrong = service.match(101L, new MatchCommand(new IdentityCommand("错误姓名", "CN_RESIDENT_ID",
            "110101199001011234", "MALE", LocalDate.of(1990, 1, 1),
            LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1))));

        assertThat(matched.status()).isEqualTo("REBIND_AVAILABLE");
        assertThat(matched.maskedPhone()).isEqualTo("138****8000");
        assertThat(wrong.status()).isEqualTo("NOT_AVAILABLE");
        assertThat(wrong.maskedPhone()).isNull();
        verify(users).selectPhonenumberById(202L);
    }

    @Test
    void shortPhoneNeverBecomesRecognizable() {
        PersonApplication application = application(false, 0, "DRAFT");
        when(applications.findOpenByUserId(101L)).thenReturn(Optional.of(application));
        when(rebinds.findExactCandidate(application.fields())).thenReturn(Optional.of(candidate()));
        when(users.selectPhonenumberById(202L)).thenReturn("1234567");

        assertThat(service.match(101L, new MatchCommand(identity())).maskedPhone()).isEqualTo("****");
    }

    @Test
    void confirmationFreezesTheCurrentBindingWithoutSwitchingIt() {
        PersonApplicationRow row = applicationRow();
        PersonRebindMapper.RebindCandidateRow candidate = candidate();
        when(rebinds.lockOpenApplication(101L)).thenReturn(row);
        when(rebinds.same(row, fields())).thenReturn(true);
        when(rebinds.findExactCandidate(fields())).thenReturn(Optional.of(candidate));
        when(rebinds.lockCandidate(candidate, fields())).thenReturn(candidate);
        when(rebinds.confirm(row, candidate, 3)).thenReturn(4);
        when(users.selectPhonenumberById(202L)).thenReturn("13800138000");

        var result = service.confirm(101L, new ConfirmCommand(identity(), 3));

        assertThat(result.version()).isEqualTo(4);
        assertThat(result.maskedPhone()).isEqualTo("138****8000");
        verify(rebinds).confirm(row, candidate, 3);
        verify(rebinds, never()).unbind(eq(202L), any());
    }

    @Test
    void submitKeepsTheOldBindingAndStartsReviewFromImmutableSnapshot() {
        PersonApplication application = application(true, 4, "DRAFT");
        PersonSubmission submission = new PersonSubmission(9101L, 9001L, 1, 101L,
            application.fields(), "manual", clock.instant());
        when(applications.lockOpenByUserId(101L)).thenReturn(application);
        when(applications.insertSubmission(eq(application), any(), eq(1), eq(clock.instant())))
            .thenReturn(submission);
        when(applications.markWaiting(9001L, 1, 4, clock.instant()))
            .thenReturn(application(true, 5, "WAITING"));

        var result = service.submit(101L, new SubmitCommand(4));

        assertThat(result.status()).isEqualTo("WAITING");
        verify(rebinds).requireFrozenCandidate(application);
        verify(rebinds, never()).unbind(eq(202L), any());
        verify(workflow).start(9001L, 9101L, 1);
    }

    @Test
    void rejectsSubmitWhenTheFrozenBindingFenceChanged() {
        PersonApplication application = application(true, 4, "DRAFT");
        when(applications.lockOpenByUserId(101L)).thenReturn(application);
        when(rebinds.requireFrozenCandidate(application))
            .thenThrow(new PersonRebindException("PERSON_REBIND_BINDING_CHANGED"));

        assertThatThrownBy(() -> service.submit(101L, new SubmitCommand(4)))
            .hasMessage("PERSON_REBIND_BINDING_CHANGED");
        verify(applications, never()).insertSubmission(any(), any(), eq(1), any());
        verify(workflow, never()).start(9001L, 9101L, 1);
    }

    private IdentityCommand identity() {
        return new IdentityCommand("张三", "CN_RESIDENT_ID", "110101199001011234", "MALE",
            LocalDate.of(1990, 1, 1), LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1));
    }

    private PersonIdentityFields fields() {
        IdentityCommand value = identity();
        return PersonIdentityFields.normalize(new PersonDraftCommand(value.fullName(), value.documentTypeCode(),
            value.documentNumber(), value.gender(), value.birthDate(), value.validFrom(), value.validUntil(), 0));
    }

    private PersonApplication application(boolean rebind, int version, String status) {
        return new PersonApplication(9001L, 101L, rebind ? 9201L : null, status, fields(), "manual", 0,
            rebind, rebind ? 9301L : null, rebind ? 2 : null, 0, version, null, null);
    }

    private PersonApplicationRow applicationRow() {
        PersonApplicationRow row = new PersonApplicationRow();
        row.setPersonApplicationId(9001L);
        row.setApplicantUserId(101L);
        row.setStatus("DRAFT");
        row.setVersion(3);
        row.setFullName(fields().fullName());
        row.setDocumentTypeCode(fields().documentTypeCode());
        row.setDocumentNumber(fields().documentNumber());
        row.setIdentityKey(fields().identityKey());
        row.setGender(fields().gender());
        row.setBirthDate(fields().birthDate());
        row.setValidFrom(fields().validFrom());
        row.setValidUntil(fields().validUntil());
        return row;
    }

    private PersonRebindMapper.RebindCandidateRow candidate() {
        PersonRebindMapper.RebindCandidateRow row = new PersonRebindMapper.RebindCandidateRow();
        row.setPersonProfileId(9201L);
        row.setPersonBindingId(9301L);
        row.setOldUserId(202L);
        row.setBindingVersion(2);
        row.setFullName(fields().fullName());
        row.setDocumentTypeCode(fields().documentTypeCode());
        row.setDocumentNumber(fields().documentNumber());
        row.setIdentityKey(fields().identityKey());
        row.setGender(fields().gender());
        row.setBirthDate(fields().birthDate());
        row.setValidFrom(fields().validFrom());
        row.setValidUntil(fields().validUntil());
        return row;
    }
}
