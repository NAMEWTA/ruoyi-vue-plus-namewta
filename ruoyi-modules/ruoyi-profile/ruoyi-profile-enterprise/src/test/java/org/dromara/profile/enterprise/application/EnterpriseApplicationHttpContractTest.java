package org.dromara.profile.enterprise.application;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class EnterpriseApplicationHttpContractTest {

    @Test
    void exposesCurrentUserOnlyRoutesAndNeverLogsIdentityBodies() {
        RequestMapping root = EnterpriseApplicationController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/profile/enterprise/application");
        assertThat(EnterpriseDraftCommand.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("userId", "applicantUserId");

        for (Method method : EnterpriseApplicationController.class.getDeclaredMethods()) {
            SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
            assertThat(permission).isNotNull();
            assertThat(permission.value()).containsExactly("profile:enterprise:apply");
            if (method.isAnnotationPresent(PostMapping.class)) {
                Log log = method.getAnnotation(Log.class);
                assertThat(log).isNotNull();
                assertThat(log.isSaveRequestData()).isFalse();
                assertThat(log.isSaveResponseData()).isFalse();
            }
        }
    }

    @Test
    void separatesReadSaveAndSubmitCommands() throws Exception {
        assertThat(EnterpriseApplicationController.class.getMethod("current").getAnnotation(GetMapping.class))
            .isNotNull();
        assertThat(EnterpriseApplicationController.class.getMethod("save", EnterpriseDraftCommand.class)
            .getAnnotation(PostMapping.class).value()).isEmpty();
        assertThat(EnterpriseApplicationController.class.getMethod("submit", EnterpriseSubmitCommand.class)
            .getAnnotation(PostMapping.class).value()).containsExactly("/submit");
        assertThat(EnterpriseApplicationController.class.getMethod("probe", EnterpriseProbeCommand.class)
            .getAnnotation(PostMapping.class).value()).containsExactly("/probe");
        assertThat(EnterpriseProbeView.class.getRecordComponents())
            .extracting(component -> component.getName()).containsExactly("status");
        assertThat(EnterpriseApplicationService.class.getMethod("save", long.class, EnterpriseDraftCommand.class)
            .isAnnotationPresent(DSTransactional.class)).isTrue();
        assertThat(EnterpriseApplicationService.class.getMethod("submit", long.class, int.class)
            .isAnnotationPresent(DSTransactional.class)).isTrue();
        assertThat(EnterpriseApplicationService.class.getMethod("handleProcessEvent",
            org.dromara.workflow.api.event.ProcessEvent.class).isAnnotationPresent(DSTransactional.class)).isTrue();
    }
}
