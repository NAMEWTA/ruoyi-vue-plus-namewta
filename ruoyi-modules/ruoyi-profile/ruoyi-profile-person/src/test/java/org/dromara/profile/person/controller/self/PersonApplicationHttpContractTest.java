package org.dromara.profile.person.controller.self;

import org.dromara.profile.person.domain.bo.PersonApplicationSaveBo;
import org.dromara.profile.person.domain.bo.PersonApplicationSubmitBo;
import org.dromara.profile.person.service.impl.PersonApplicationServiceImpl;
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
class PersonApplicationHttpContractTest {

    @Test
    void exposesCurrentUserOnlyRoutesAndNeverLogsIdentityBodies() {
        RequestMapping root = PersonApplicationController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/profile/person/application");
        assertThat(PersonApplicationSaveBo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("userId", "applicantUserId");

        for (Method method : PersonApplicationController.class.getDeclaredMethods()) {
            SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
            assertThat(permission).isNotNull();
            assertThat(permission.value()).containsExactly("profile:person:apply");
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
        assertThat(PersonApplicationController.class.getMethod("current").getAnnotation(GetMapping.class))
            .isNotNull();
        assertThat(PersonApplicationController.class.getMethod("save", PersonApplicationSaveBo.class)
            .getAnnotation(PostMapping.class).value()).isEmpty();
        assertThat(PersonApplicationController.class.getMethod("submit", PersonApplicationSubmitBo.class)
            .getAnnotation(PostMapping.class).value()).containsExactly("/submit");
        assertThat(PersonApplicationServiceImpl.class.getMethod("save", long.class, PersonApplicationSaveBo.class)
            .isAnnotationPresent(DSTransactional.class)).isTrue();
        assertThat(PersonApplicationServiceImpl.class.getMethod("submit", long.class, int.class)
            .isAnnotationPresent(DSTransactional.class)).isTrue();
        assertThat(PersonApplicationServiceImpl.class.getMethod("handleProcessEvent",
            org.dromara.workflow.api.event.ProcessEvent.class).isAnnotationPresent(DSTransactional.class)).isTrue();
    }
}
