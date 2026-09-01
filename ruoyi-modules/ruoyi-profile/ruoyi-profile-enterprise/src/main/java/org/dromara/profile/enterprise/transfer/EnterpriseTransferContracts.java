package org.dromara.profile.enterprise.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class EnterpriseTransferContracts {

    private EnterpriseTransferContracts() {
    }

    public record SendCommand(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Pattern(regexp = "[0-9A-Za-z]{4}") String documentLastFour,
        @NotBlank @Size(max = 32) String phone
    ) {
    }

    public record ConfirmCommand(
        @NotBlank @Size(max = 64) String challengeId,
        @NotBlank @Pattern(regexp = "\\d{6}") String code
    ) {
    }

    public record TransferView(String status, String challengeId, Long expiresInSeconds) {

        public static TransferView sent(String challengeId) {
            return new TransferView("SENT", challengeId, 300L);
        }

        public static TransferView status(String status) {
            return new TransferView(status, null, null);
        }
    }
}
