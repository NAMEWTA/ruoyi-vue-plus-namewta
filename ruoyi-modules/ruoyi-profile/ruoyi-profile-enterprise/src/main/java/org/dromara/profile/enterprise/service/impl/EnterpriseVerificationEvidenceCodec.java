package org.dromara.profile.enterprise.service.impl;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EnterpriseVerificationEvidenceCodec {

    private static final int SCHEMA_VERSION = 1;

    private final JsonMapper jsonMapper;

    public EnterpriseVerificationEvidenceCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String encode(String callbackDigest, String providerEvidenceJson) {
        return jsonMapper.writeValueAsString(
            new StoredEvidence(SCHEMA_VERSION, callbackDigest, providerEvidenceJson));
    }

    public DecodedEvidence decode(String storedJson) {
        if (storedJson == null) {
            return new DecodedEvidence(null, null);
        }
        try {
            StoredEvidence evidence = jsonMapper.readValue(storedJson, StoredEvidence.class);
            if (evidence.schemaVersion() == SCHEMA_VERSION) {
                return new DecodedEvidence(evidence.callbackDigest(), evidence.providerEvidenceJson());
            }
        } catch (RuntimeException ignored) {
            // Rows written before the evidence envelope remain readable as provider evidence.
        }
        return new DecodedEvidence(null, storedJson);
    }

    private record StoredEvidence(
        int schemaVersion,
        String callbackDigest,
        String providerEvidenceJson
    ) {
    }

    public record DecodedEvidence(String callbackDigest, String providerEvidenceJson) {
    }
}
