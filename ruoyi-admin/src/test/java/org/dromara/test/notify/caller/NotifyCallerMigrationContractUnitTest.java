package org.dromara.test.notify.caller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class NotifyCallerMigrationContractUnitTest {

    @Test
    void notificationCallersUseNotifyClientAndDeclareDirectDependency() throws Exception {
        String captcha = source("ruoyi-admin/src/main/java/org/dromara/web/controller/CaptchaController.java");
        String workflow = source("ruoyi-modules/ruoyi-workflow/src/main/java/org/dromara/workflow/service/impl/FlwCommonServiceImpl.java");
        String mailDemo = source("ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/controller/MailSendController.java");
        String smsDemo = source("ruoyi-modules/ruoyi-demo/src/main/java/org/dromara/demo/controller/SmsController.java");

        assertAll(
            () -> assertTrue(captcha.contains("NotifyClient")),
            () -> assertFalse(captcha.contains("MailBuilder")),
            () -> assertFalse(captcha.contains("SmsFactory")),
            () -> assertTrue(workflow.contains("NotifyClient")),
            () -> assertFalse(workflow.contains("MailBuilder")),
            () -> assertTrue(mailDemo.contains("NotifyClient")),
            () -> assertFalse(mailDemo.contains("MailBuilder")),
            () -> assertTrue(smsDemo.contains("NotifyClient")),
            () -> assertFalse(smsDemo.contains("smsBlend.sendMessage"))
        );

        assertTrue(source("ruoyi-admin/pom.xml").contains("<artifactId>ruoyi-common-notify</artifactId>"));
        String workflowPom = source("ruoyi-modules/ruoyi-workflow/pom.xml");
        assertTrue(workflowPom.contains("<artifactId>ruoyi-common-notify</artifactId>"));
        assertFalse(workflowPom.contains("<artifactId>ruoyi-common-mail</artifactId>"));
        assertFalse(workflowPom.contains("<artifactId>ruoyi-common-sms</artifactId>"));
        String demoPom = source("ruoyi-modules/ruoyi-demo/pom.xml");
        assertTrue(demoPom.contains("<artifactId>ruoyi-common-notify</artifactId>"));
        assertFalse(demoPom.contains("<artifactId>ruoyi-common-mail</artifactId>"));
        assertTrue(demoPom.contains("<artifactId>ruoyi-common-sms</artifactId>"));
    }

    private String source(String relativePath) throws Exception {
        return Files.readString(repositoryRoot().resolve(relativePath));
    }

    private Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        return current.getFileName().toString().equals("ruoyi-admin") ? current.getParent() : current;
    }
}
