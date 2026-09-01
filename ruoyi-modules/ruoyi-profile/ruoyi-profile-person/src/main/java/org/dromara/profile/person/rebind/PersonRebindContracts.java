package org.dromara.profile.person.rebind;

import java.time.LocalDate;

public final class PersonRebindContracts {

    private PersonRebindContracts() {
    }

    public record ProbeCommand(String documentTypeCode, String documentNumber) {
    }

    public record ProbeView(String status) {
    }

    public record IdentityCommand(
        String fullName,
        String documentTypeCode,
        String documentNumber,
        String gender,
        LocalDate birthDate,
        LocalDate validFrom,
        LocalDate validUntil
    ) {
    }

    public record MatchCommand(IdentityCommand identity) {
    }

    public record MatchView(String status, String maskedPhone) {
    }

    public record ConfirmCommand(IdentityCommand identity, int expectedVersion) {
    }

    public record ConfirmationView(String status, String maskedPhone, int version) {
    }

    public record SubmitCommand(int expectedVersion) {
    }

    public record SubmissionView(String status, int snapshotVersion, int version) {
    }

    public record UnbindView(String status) {
    }
}
