package org.dromara.test.migration.password;

import org.dromara.system.password.PasswordDefaultMode;
import org.dromara.system.password.PasswordPolicy;
import org.dromara.system.password.PasswordPolicyConfigParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class PasswordMigrationUnitTest {

    private static final String MARKER = "NAMEWTA-PASSWORD-DSL-001";
    private static final String HISTORICAL_PREFIX_SHA256 =
        "698675a3a16598df7313b90a5b267bb3cbfe9fe1a8e489737752189b3f58a81f";
    private static final int HISTORICAL_PREFIX_BYTES = 15_370;
    private static final Pattern POLICY_VALUE = Pattern.compile(
        "select\\s+\\d+,\\s*'统一密码策略'.*?'sys\\.user\\.passwordPolicy',\\s*'([^']+)'",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    void appendsMigrationWithoutChangingHistoricalDml() throws Exception {
        String dml = readDml();
        int marker = dml.indexOf(MARKER);

        assertTrue(marker > 0, "missing password migration marker");
        byte[] bytes = dml.getBytes(StandardCharsets.UTF_8);
        assertTrue(bytes.length > HISTORICAL_PREFIX_BYTES);
        assertEquals(HISTORICAL_PREFIX_SHA256,
            sha256(Arrays.copyOfRange(bytes, 0, HISTORICAL_PREFIX_BYTES)));
        assertTrue(dml.substring(marker).contains("2026-08-28_"));
        assertTrue(dml.substring(marker).toLowerCase(Locale.ROOT).contains("random_bytes"));
        assertFalse(dml.substring(marker).contains("'123456'"));
    }

    @Test
    void publishesValidRandomV1PolicyWithinDatabaseLimit() throws Exception {
        String migration = migration(readDml());
        Matcher matcher = POLICY_VALUE.matcher(migration);

        assertTrue(matcher.find(), "missing password policy config insert");
        String json = matcher.group(1);
        assertTrue(json.length() < 500, "sys_config.config_value exceeds its varchar(500) contract");
        PasswordPolicy policy = new PasswordPolicyConfigParser(JsonMapper.builder().build()).parse(json);
        assertEquals(1, policy.version());
        assertEquals(PasswordDefaultMode.RANDOM, policy.defaultPassword().mode());
        assertEquals(null, policy.defaultPassword().fixedValue());
        assertNotEquals("123456", json);
    }

    @Test
    void declaresIndependentPermissionWithoutGrantingOrdinaryRoles() throws Exception {
        String migration = migration(readDml()).toLowerCase(Locale.ROOT);

        assertTrue(migration.contains("'system:user:temporarypassword'"));
        assertTrue(migration.contains("1761400000000000100"), "permission must belong to user management");
        assertFalse(migration.contains("system:user:resetpwd"));
        assertFalse(migration.contains("insert into sys_role_menu"));
        assertTrue(migration.contains("不自动授予普通角色"));
    }

    @Test
    void documentsPreflightRepeatRollbackAndForwardCompensation() throws Exception {
        String migration = migration(readDml());

        for (String contract : new String[]{"执行前置：", "适用范围：", "重复执行：是", "回滚方式：",
            "前置不符", "回滚步骤", "前向补偿"}) {
            assertTrue(migration.contains(contract), () -> "missing migration contract: " + contract);
        }
        assertTrue(migration.toLowerCase(Locale.ROOT).contains("temporary table"));
        assertTrue(migration.toLowerCase(Locale.ROOT).contains("check (preflight_ok = 1)"));
    }

    private static String migration(String dml) {
        int marker = dml.indexOf(MARKER);
        assertTrue(marker >= 0, "missing password migration marker");
        return dml.substring(marker);
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        return HexFormat.of().formatHex(digest);
    }

    private static String readDml() throws IOException {
        Path current = Path.of(System.getProperty("user.dir"));
        Path repository = current.getFileName().toString().equals("ruoyi-admin") ? current.getParent() : current;
        return Files.readString(repository.resolve("script/sql/namewta/DML.sql"));
    }
}
