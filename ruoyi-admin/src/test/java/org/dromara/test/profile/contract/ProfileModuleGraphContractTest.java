package org.dromara.test.profile.contract;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ProfileModuleGraphContractTest {

    @Test
    void reactorContainsProfileAggregateBomAndBothLeaves() throws Exception {
        Path root = repositoryRoot();

        assertThat(Files.readString(root.resolve("ruoyi-modules/pom.xml")))
            .contains("<module>ruoyi-profile</module>");
        assertThat(Files.readString(root.resolve("ruoyi-modules/ruoyi-profile/pom.xml")))
            .contains("<module>ruoyi-profile-bom</module>")
            .contains("<module>ruoyi-profile-person</module>")
            .contains("<module>ruoyi-profile-enterprise</module>");
        assertThat(Files.readString(root.resolve("ruoyi-modules/ruoyi-profile/ruoyi-profile-bom/pom.xml")))
            .contains("<artifactId>ruoyi-profile-person</artifactId>")
            .contains("<artifactId>ruoyi-profile-enterprise</artifactId>");
    }

    @Test
    void fullAndCoreBundlesBothAssembleProfileWhileWorkflowRemainsFullOnly() throws Exception {
        String adminPom = Files.readString(repositoryRoot().resolve("ruoyi-admin/pom.xml"));
        String full = profileBody(adminPom, "bundle-full");
        String core = profileBody(adminPom, "bundle-core");

        assertThat(full).contains("ruoyi-profile-person", "ruoyi-profile-enterprise", "ruoyi-workflow");
        assertThat(core).contains("ruoyi-profile-person", "ruoyi-profile-enterprise")
            .doesNotContain("ruoyi-workflow");
    }

    private static String profileBody(String pom, String id) {
        int idIndex = pom.indexOf("<id>" + id + "</id>");
        int start = pom.lastIndexOf("<profile>", idIndex);
        int end = pom.indexOf("</profile>", idIndex);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(idIndex);
        return pom.substring(start, end);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("ruoyi-admin/pom.xml"))) {
            current = current.getParent();
        }
        assertThat(current).as("backend repository root").isNotNull();
        return current;
    }
}
