package org.dromara.test.notify.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.common.log.annotation.Log;
import org.dromara.system.controller.monitor.SysNotifyController;
import org.dromara.system.notify.support.NotifyTargetMasker;
import org.dromara.system.notify.support.NotifyLogSanitizer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class NotifyMonitorContractUnitTest {

    @Test
    void listMaskingNeverReturnsFullPhysicalTarget() {
        assertEquals("138****5678", NotifyTargetMasker.mask("PHONE", "13812345678"));
        assertEquals("s***@example.com", NotifyTargetMasker.mask("EMAIL", "secret@example.com"));
        assertNotEquals("open-id-secret", NotifyTargetMasker.mask("OPEN_ID", "open-id-secret"));
        String sanitized = NotifyLogSanitizer.error(
            "provider failed https://oss.example/file?X-Amz-Signature=secret token=abc");
        assertFalse(sanitized.contains("https://"));
        assertFalse(sanitized.contains("token=abc"));
    }

    @Test
    void controllerUsesGlobalPermissionsAndAuditsDestructiveOperations() throws Exception {
        assertArrayEquals(new String[]{"/monitor/notify"},
            SysNotifyController.class.getAnnotation(RequestMapping.class).value());

        Method list = SysNotifyController.class.getDeclaredMethod("list",
            org.dromara.system.notify.bo.SysNotifyQuery.class,
            org.dromara.common.mybatis.core.page.PageQuery.class);
        assertArrayEquals(new String[]{"system:notify:list"}, list.getAnnotation(SaCheckPermission.class).value());
        assertNotNull(list.getAnnotation(GetMapping.class));

        Method detail = SysNotifyController.class.getDeclaredMethod("detail", Long.class);
        assertArrayEquals(new String[]{"system:notify:query"}, detail.getAnnotation(SaCheckPermission.class).value());

        Method remove = SysNotifyController.class.getDeclaredMethod("remove", Long[].class);
        assertArrayEquals(new String[]{"system:notify:remove"}, remove.getAnnotation(SaCheckPermission.class).value());
        assertNotNull(remove.getAnnotation(DeleteMapping.class));
        assertNotNull(remove.getAnnotation(Log.class));

        Method clean = SysNotifyController.class.getDeclaredMethod("clean");
        assertArrayEquals(new String[]{"system:notify:remove"}, clean.getAnnotation(SaCheckPermission.class).value());
        assertNotNull(clean.getAnnotation(Log.class));
    }
}
