package org.dromara.test.openapi.credential;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class OpenApiCredentialSqlContractTest {

    private static final int DDL_BASELINE_BYTES = 14825;
    private static final int DML_BASELINE_BYTES = 26695;

    @Test
    void sqlIsAppendOnlyAndContainsCredentialSchemaAndPermissions() throws Exception {
        Path repository = Path.of(System.getProperty("user.dir")).getParent();
        byte[] ddl = Files.readAllBytes(repository.resolve("script/sql/namewta/DDL.sql"));
        byte[] dml = Files.readAllBytes(repository.resolve("script/sql/namewta/DML.sql"));

        assertThat(hashPrefix(ddl, DDL_BASELINE_BYTES))
            .isEqualTo("4cfc4d1d86c48aa3bf6bfdf65f8642c8b528d8aa5c452516090655188251c3d1");
        assertThat(hashPrefix(dml, DML_BASELINE_BYTES))
            .isEqualTo("f440bdb77065a6119973abf80ac21278d2680ce86a3ebe0a5d209509d8ed258b");

        String ddlText = new String(ddl);
        assertThat(ddlText).contains("create table sys_open_api_credential", "open_api_credential_id",
            "active_owner_user_id", "secret_ciphertext", "secret_nonce", "secret_tag", "kek_version",
            "unique key uk_sys_open_api_credential_active_owner", "unique key uk_sys_open_api_credential_app_key");
        String dmlText = new String(dml);
        assertThat(dmlText).contains("system/openApi/index", "system:openApi:list", "system:openApi:query",
            "system:openApi:add", "system:openApi:edit", "system:openApi:remove", "system:openApi:self");
    }

    private static String hashPrefix(byte[] bytes, int length) throws Exception {
        assertThat(bytes.length).isGreaterThan(length);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            java.util.Arrays.copyOf(bytes, length)));
    }
}
