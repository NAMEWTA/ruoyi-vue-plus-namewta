package org.dromara.profile.person.controller.self;

import org.dromara.profile.person.listener.PersonRebindProcessListener;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.dromara.common.log.annotation.Log;
import org.dromara.profile.person.domain.vo.PersonRebindConfirmationVo;
import org.dromara.profile.person.domain.vo.PersonRebindMatchVo;
import org.dromara.profile.person.domain.vo.PersonRebindProbeVo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class PersonRebindHttpContractTest {

    @Test
    void exposesOnlyCurrentAccountCommandsWithOneUsablePermission() {
        RequestMapping root = PersonRebindController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/profile/person/rebind");

        for (Method method : PersonRebindController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(PostMapping.class)).isNotNull();
            SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
            assertThat(permission).isNotNull();
            assertThat(permission.value()).containsExactly("profile:person:apply");
            assertThat(method.getParameterTypes())
                .doesNotContain(long.class, Long.class);
            Log log = method.getAnnotation(Log.class);
            assertThat(log).isNotNull();
            assertThat(log.isSaveRequestData()).isFalse();
            assertThat(log.isSaveResponseData()).isFalse();
        }
    }

    @Test
    void privacyResponsesNeverExposeInternalIdentifiersOrFullAccountData() {
        assertThat(PersonRebindProbeVo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("status");
        assertThat(PersonRebindMatchVo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("status", "maskedPhone");
        assertThat(PersonRebindConfirmationVo.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("status", "maskedPhone", "version")
            .doesNotContain("profileId", "bindingId", "userId", "phone");
    }

    @Test
    void finalPublicationRunsBeforeTheOrdinaryPersonListener() throws Exception {
        Method handle = PersonRebindProcessListener.class.getMethod("handle",
            org.dromara.workflow.api.event.ProcessEvent.class);
        assertThat(handle.isAnnotationPresent(DSTransactional.class)).isTrue();
        assertThat(handle.getAnnotation(Order.class).value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
