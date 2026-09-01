package org.dromara.profile.enterprise.material;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("dev")
class EnterpriseMaterialControllerContractTest {

    @Test
    void exposesNoDeleteRouteAndKeepsEnterprisePermissionClosed() {
        RequestMapping mapping = EnterpriseMaterialController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/profile/enterprise/materials");
        assertThat(Arrays.stream(EnterpriseMaterialController.class.getDeclaredMethods()))
            .noneMatch(method -> method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) != null);
        assertThat(Arrays.stream(EnterpriseMaterialController.class.getDeclaredMethods())
            .filter(method -> method.getAnnotation(PostMapping.class) != null)
            .allMatch(method -> Arrays.asList(method.getAnnotation(SaCheckPermission.class).value())
                .contains("profile:enterprise:material"))).isTrue();
    }

    @Test
    void adapterFixesAllCommandsToEnterpriseProfileType() throws Exception {
        ProfileMaterialPort port = mock(ProfileMaterialPort.class);
        EnterpriseMaterialController controller = new EnterpriseMaterialController(port);

        assertThat(controller.profileType()).isEqualTo(ProfileType.ENTERPRISE);
        Method download = EnterpriseMaterialController.class.getDeclaredMethod(
            "accessUrl", ProfileMaterialPort.MaterialOwnerType.class, Long.class, Long.class);
        assertThat(download.getAnnotation(GetMapping.class)).isNotNull();
    }
}
