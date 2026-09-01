package org.dromara.profile.person.rebind;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.person.application.DocumentTypeRule;
import org.dromara.profile.person.application.PersonApplication;
import org.dromara.profile.person.application.PersonApplicationRepository;
import org.dromara.profile.person.application.PersonDraftCommand;
import org.dromara.profile.person.application.PersonIdentityFields;
import org.dromara.profile.person.application.PersonSubmission;
import org.dromara.profile.person.application.PersonWorkflowGateway;
import org.dromara.profile.person.persistence.row.PersonApplicationRow;
import org.dromara.profile.person.rebind.PersonRebindContracts.ConfirmCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.ConfirmationView;
import org.dromara.profile.person.rebind.PersonRebindContracts.IdentityCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.MatchCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.MatchView;
import org.dromara.profile.person.rebind.PersonRebindContracts.ProbeCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.ProbeView;
import org.dromara.profile.person.rebind.PersonRebindContracts.SubmissionView;
import org.dromara.profile.person.rebind.PersonRebindContracts.SubmitCommand;
import org.dromara.profile.person.rebind.PersonRebindContracts.UnbindView;
import org.dromara.profile.person.verification.PersonVerificationAttemptCoordinator;
import org.dromara.profile.person.verification.PersonVerificationException;
import org.dromara.profile.person.verification.PersonVerificationProviderRegistry;
import org.dromara.profile.person.verification.PersonVerificationStartAttemptCommand;
import org.dromara.system.api.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PersonRebindService {

    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "BACK", "CANCEL");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");

    private final PersonRebindRepository rebinds;
    private final PersonApplicationRepository applications;
    private final ProfileMaterialPort materials;
    private final PersonVerificationProviderRegistry providers;
    private final PersonVerificationAttemptCoordinator attempts;
    private final PersonWorkflowGateway workflow;
    private final UserService users;
    private final Clock clock;

    @Autowired
    public PersonRebindService(PersonRebindRepository rebinds, PersonApplicationRepository applications,
                               ProfileMaterialPort materials, PersonVerificationProviderRegistry providers,
                               PersonVerificationAttemptCoordinator attempts, PersonWorkflowGateway workflow,
                               UserService users) {
        this(rebinds, applications, materials, providers, attempts, workflow, users, Clock.systemUTC());
    }

    PersonRebindService(PersonRebindRepository rebinds, PersonApplicationRepository applications,
                        ProfileMaterialPort materials, PersonVerificationProviderRegistry providers,
                        PersonVerificationAttemptCoordinator attempts, PersonWorkflowGateway workflow,
                        UserService users, Clock clock) {
        this.rebinds = rebinds;
        this.applications = applications;
        this.materials = materials;
        this.providers = providers;
        this.attempts = attempts;
        this.workflow = workflow;
        this.users = users;
        this.clock = clock;
    }

    public ProbeView probe(ProbeCommand command) {
        if (command == null) {
            throw failure("PERSON_REBIND_PROBE_INVALID");
        }
        String type = upper(command.documentTypeCode());
        String number = upper(command.documentNumber());
        if (type == null || number == null) {
            throw failure("PERSON_REBIND_PROBE_INVALID");
        }
        return new ProbeView(rebinds.probeStatus(type + ":" + number));
    }

    public MatchView match(long userId, MatchCommand command) {
        PersonIdentityFields fields = safeIdentity(command == null ? null : command.identity());
        if (fields == null || applications.findEffectiveProfileIdByUser(userId) != null) {
            return unavailable();
        }
        Optional<PersonApplication> current = applications.findOpenByUserId(userId);
        if (current.isEmpty() || !EDITABLE_STATUSES.contains(current.get().status())
            || !same(current.get().fields(), fields)) {
            return unavailable();
        }
        Optional<PersonRebindMapper.RebindCandidateRow> candidate = rebinds.findExactCandidate(fields);
        if (candidate.isEmpty() || candidate.get().getOldUserId() == userId) {
            return unavailable();
        }
        return new MatchView("REBIND_AVAILABLE", maskedPhone(candidate.get().getOldUserId()));
    }

    @DSTransactional
    public ConfirmationView confirm(long userId, ConfirmCommand command) {
        requireUserId(userId);
        if (command == null) {
            throw failure("PERSON_REBIND_NOT_AVAILABLE");
        }
        PersonIdentityFields fields = requireIdentity(command.identity());
        PersonApplicationRow application = rebinds.lockOpenApplication(userId);
        if (!EDITABLE_STATUSES.contains(application.getStatus())
            || intValue(application.getVersion()) != command.expectedVersion()
            || !rebinds.same(application, fields)) {
            throw failure("PERSON_REBIND_NOT_AVAILABLE");
        }
        rebinds.requireApplicantUnbound(userId);
        PersonRebindMapper.RebindCandidateRow candidate = rebinds.findExactCandidate(fields)
            .filter(value -> value.getOldUserId() != userId)
            .orElseThrow(() -> failure("PERSON_REBIND_NOT_AVAILABLE"));
        candidate = rebinds.lockCandidate(candidate, fields);
        int version = rebinds.confirm(application, candidate, command.expectedVersion());
        return new ConfirmationView("CONFIRMED", maskedPhone(candidate.getOldUserId()), version);
    }

    @DSTransactional
    public SubmissionView submit(long userId, SubmitCommand command) {
        requireUserId(userId);
        if (command == null) {
            throw failure("PERSON_REBIND_SUBMIT_INVALID");
        }
        PersonApplication application = applications.lockOpenByUserId(userId);
        if (application.applicantUserId() != userId || !EDITABLE_STATUSES.contains(application.status())
            || application.version() != command.expectedVersion()) {
            throw failure("PERSON_REBIND_VERSION_CONFLICT");
        }
        validateComplete(application.fields());
        requireProviderEnabled(application.providerCode());
        rebinds.requireApplicantUnbound(userId);
        rebinds.requireFrozenCandidate(application);

        MaterialOwnerKey working = owner(MaterialOwnerType.WORKING, application.personApplicationId());
        materials.validateRequired(working, application.fields().documentTypeCode(), Set.of("ALWAYS"));
        int snapshotVersion = application.submissionSeq() + 1;
        Instant submittedTime = clock.instant();
        PersonSubmission submission = applications.insertSubmission(application, application.fields(),
            snapshotVersion, submittedTime);
        materials.snapshotImmutable(working, owner(MaterialOwnerType.SUBMISSION,
            submission.personSubmissionId()));
        PersonApplication waiting = applications.markWaiting(application.personApplicationId(), snapshotVersion,
            command.expectedVersion(), submittedTime);
        startVerificationAttempt(new PersonVerificationStartAttemptCommand(application.personApplicationId(),
            submission.personSubmissionId(), fingerprint(application.fields(), snapshotVersion)));
        workflow.start(application.personApplicationId(), submission.personSubmissionId(), snapshotVersion);
        return new SubmissionView(waiting.status(), waiting.submissionSeq(), waiting.version());
    }

    @DSTransactional
    public UnbindView unbind(long userId) {
        requireUserId(userId);
        rebinds.unbind(userId, clock.instant());
        return new UnbindView("UNBOUND");
    }

    private PersonIdentityFields safeIdentity(IdentityCommand command) {
        try {
            PersonIdentityFields fields = normalize(command);
            validateComplete(fields);
            return fields;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private PersonIdentityFields requireIdentity(IdentityCommand command) {
        try {
            PersonIdentityFields fields = normalize(command);
            validateComplete(fields);
            return fields;
        } catch (RuntimeException exception) {
            throw failure("PERSON_REBIND_NOT_AVAILABLE");
        }
    }

    private PersonIdentityFields normalize(IdentityCommand command) {
        if (command == null) {
            throw failure("PERSON_REBIND_IDENTITY_REQUIRED");
        }
        return PersonIdentityFields.normalize(new PersonDraftCommand(command.fullName(), command.documentTypeCode(),
            command.documentNumber(), command.gender(), command.birthDate(), command.validFrom(),
            command.validUntil(), 0));
    }

    private void validateComplete(PersonIdentityFields fields) {
        if (fields.fullName() == null || fields.documentTypeCode() == null || fields.documentNumber() == null
            || fields.gender() == null || fields.birthDate() == null || fields.fullName().length() > 100
            || fields.documentNumber().length() > 128 || !GENDERS.contains(fields.gender())) {
            throw failure("PERSON_REBIND_IDENTITY_INVALID");
        }
        DocumentTypeRule rule = applications.findDocumentType(fields.documentTypeCode())
            .orElseThrow(() -> failure("PERSON_REBIND_IDENTITY_INVALID"));
        if (!Pattern.matches(rule.numberPattern(), fields.documentNumber())) {
            throw failure("PERSON_REBIND_IDENTITY_INVALID");
        }
        if (rule.validityRequired() && (fields.validFrom() == null || fields.validUntil() == null)) {
            throw failure("PERSON_REBIND_IDENTITY_INVALID");
        }
        LocalDate today = LocalDate.now(clock);
        if (fields.birthDate().isAfter(today)
            || fields.validFrom() != null && fields.validUntil() != null
            && (fields.validFrom().isAfter(fields.validUntil()) || fields.validFrom().isAfter(today)
            || fields.validUntil().isBefore(today))) {
            throw failure("PERSON_REBIND_IDENTITY_INVALID");
        }
    }

    private void requireProviderEnabled(String providerCode) {
        try {
            providers.requireEnabled(providerCode);
        } catch (PersonVerificationException exception) {
            throw new PersonRebindException("PERSON_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private void startVerificationAttempt(PersonVerificationStartAttemptCommand command) {
        try {
            attempts.startAttempt(command);
        } catch (PersonVerificationException exception) {
            throw new PersonRebindException("PERSON_PROVIDER_UNAVAILABLE", exception);
        }
    }

    private String maskedPhone(long oldUserId) {
        String phone = text(users.selectPhonenumberById(oldUserId));
        if (phone == null || phone.length() < 8) {
            return "****";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 7)
            + phone.substring(phone.length() - 4);
    }

    private String fingerprint(PersonIdentityFields fields, int snapshotVersion) {
        String canonical = fields.identityKey() + "\n" + snapshotVersion + "\nREBIND";
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private MaterialOwnerKey owner(MaterialOwnerType type, long ownerId) {
        return new MaterialOwnerKey(ProfileType.PERSON, type, ownerId);
    }

    private boolean same(PersonIdentityFields first, PersonIdentityFields second) {
        return first.equals(second);
    }

    private MatchView unavailable() {
        return new MatchView("NOT_AVAILABLE", null);
    }

    private String upper(String value) {
        String normalized = text(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String text(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private void requireUserId(long userId) {
        if (userId <= 0) {
            throw failure("PERSON_USER_INVALID");
        }
    }

    private PersonRebindException failure(String category) {
        return new PersonRebindException(category);
    }
}
