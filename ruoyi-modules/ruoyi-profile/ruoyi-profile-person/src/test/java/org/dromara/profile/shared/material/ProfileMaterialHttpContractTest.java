package org.dromara.profile.shared.material;

import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ProfileMaterialHttpContractTest {

    @Test
    void materialControllersExposeNoPhysicalDeleteRoute() {
        assertThat(PersonMaterialController.class.getAnnotation(RequestMapping.class).value())
            .containsExactly("/profile/person/materials");
        assertThat(MaterialTagController.class.getAnnotation(RequestMapping.class).value())
            .containsExactly("/profile/material-tags");
        assertThat(Arrays.stream(PersonMaterialController.class.getDeclaredMethods()))
            .noneMatch(method -> method.getAnnotation(DeleteMapping.class) != null);
        assertThat(Arrays.stream(MaterialTagController.class.getDeclaredMethods()))
            .noneMatch(method -> method.getAnnotation(DeleteMapping.class) != null);
    }

    @Test
    void everyMaterialMutationExcludesRequestAndResponseBodiesFromOperationLogs() {
        assertSafeMutationLogs(PersonMaterialController.class);
        assertSafeMutationLogs(MaterialTagController.class);
    }

    private void assertSafeMutationLogs(Class<?> controller) {
        Arrays.stream(controller.getDeclaredMethods())
            .filter(method -> method.getAnnotation(PostMapping.class) != null)
            .forEach(method -> {
                Log log = method.getAnnotation(Log.class);
                assertThat(log).as(controller.getSimpleName() + "." + method.getName()).isNotNull();
                assertThat(log.isSaveRequestData()).isFalse();
                assertThat(log.isSaveResponseData()).isFalse();
            });
    }
}
