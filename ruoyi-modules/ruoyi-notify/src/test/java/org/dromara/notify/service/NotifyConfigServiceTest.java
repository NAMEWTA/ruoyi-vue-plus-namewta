package org.dromara.notify.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.notify.dao.NotifyConfigDao;
import org.dromara.notify.domain.bo.NotifyChannelAccountBo;
import org.dromara.notify.domain.bo.NotifySceneBindingBo;
import org.dromara.notify.domain.entity.NotifyChannelAccount;
import org.dromara.notify.domain.entity.NotifySceneBinding;
import org.dromara.notify.domain.vo.NotifyChannelAccountVo;
import org.dromara.notify.port.SmsBlendRegistryPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 渠道账号密钥不回显、空白保持原值，模板限额不能超过账号限额。
 */
@Tag("dev")
class NotifyConfigServiceTest {

    @Test
    void accountVoOmitsSecretFields() {
        NotifyConfigDao dao = mock(NotifyConfigDao.class);
        NotifyConfigService service = new NotifyConfigService(dao, mock(SmsBlendRegistryPort.class));
        NotifyChannelAccount account = account();
        account.setMailPass("smtp-secret");
        account.setAccessKeySecret("sms-secret");
        when(dao.findAccount(8L)).thenReturn(account);

        NotifyChannelAccountVo vo = service.getAccount(8L);
        String json = JsonUtils.toJsonString(vo);

        assertTrue(vo.isMailPassSet());
        assertTrue(vo.isAccessKeySecretSet());
        assertFalse(json.contains("smtp-secret"));
        assertFalse(json.contains("sms-secret"));
        assertFalse(json.contains("\"mailPass\":"));
        assertFalse(json.contains("\"accessKeySecret\":"));
    }

    @Test
    void blankSecretOnEditKeepsPreviousValue() {
        NotifyConfigDao dao = mock(NotifyConfigDao.class);
        NotifyConfigService service = new NotifyConfigService(dao, mock(SmsBlendRegistryPort.class));
        NotifyChannelAccount current = account();
        current.setMailPass("keep-me");
        current.setAccessKeySecret("keep-sms");
        when(dao.findAccount(8L)).thenReturn(current);
        when(dao.update(any(NotifyChannelAccount.class))).thenReturn(1);

        NotifyChannelAccountBo bo = new NotifyChannelAccountBo();
        bo.setAccountId(8L);
        bo.setChannel("MAIL");
        bo.setConfigKey("smtp-main");
        bo.setEnabled("Y");
        bo.setMinuteMax(30);
        bo.setMailPass("");
        bo.setAccessKeySecret(" ");
        service.updateAccount(bo);

        ArgumentCaptor<NotifyChannelAccount> captor = ArgumentCaptor.forClass(NotifyChannelAccount.class);
        verify(dao).update(captor.capture());
        assertEquals("keep-me", captor.getValue().getMailPass());
        assertEquals("keep-sms", captor.getValue().getAccessKeySecret());
    }

    @Test
    void templateMinuteMaxCannotExceedAccountCap() {
        NotifyConfigDao dao = mock(NotifyConfigDao.class);
        NotifyConfigService service = new NotifyConfigService(dao, mock(SmsBlendRegistryPort.class));
        NotifyChannelAccount account = account();
        account.setMinuteMax(10);
        when(dao.findAccount(8L)).thenReturn(account);

        NotifySceneBindingBo bo = new NotifySceneBindingBo();
        bo.setSceneCode("auth-captcha");
        bo.setChannel("MAIL");
        bo.setAccountId(8L);
        bo.setMailSubject("码 ${code}");
        bo.setMailBody("${expireMinutes}");
        bo.setTemplateMinuteMax(20);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.saveBinding(bo));
        assertTrue(exception.getMessage().contains("不能超过账号上限"));
    }

    @Test
    void mailBindingRejectsRenamedRequiredTokenAndAcceptsMovedToken() {
        NotifyConfigDao dao = mock(NotifyConfigDao.class);
        NotifyConfigService service = new NotifyConfigService(dao, mock(SmsBlendRegistryPort.class));
        when(dao.findAccount(8L)).thenReturn(account());
        when(dao.findBinding("auth-captcha", "MAIL")).thenReturn(null);
        when(dao.insert(any(NotifySceneBinding.class))).thenReturn(1);

        NotifySceneBindingBo renamed = new NotifySceneBindingBo();
        renamed.setSceneCode("auth-captcha");
        renamed.setChannel("MAIL");
        renamed.setAccountId(8L);
        renamed.setMailSubject("码 ${code}");
        renamed.setMailBody("分钟 ${minutes}");
        renamed.setTemplateMinuteMax(10);
        ServiceException missing = assertThrows(ServiceException.class, () -> service.saveBinding(renamed));
        assertTrue(missing.getMessage().contains("expireMinutes") || missing.getMessage().contains("未声明"));

        NotifySceneBindingBo moved = new NotifySceneBindingBo();
        moved.setSceneCode("auth-captcha");
        moved.setChannel("MAIL");
        moved.setAccountId(8L);
        moved.setMailSubject("${expireMinutes}");
        moved.setMailBody("验证码 ${code}");
        moved.setTemplateMinuteMax(10);
        assertEquals(1, service.saveBinding(moved));
    }

    private NotifyChannelAccount account() {
        NotifyChannelAccount account = new NotifyChannelAccount();
        account.setAccountId(8L);
        account.setChannel("MAIL");
        account.setConfigKey("smtp-main");
        account.setEnabled("Y");
        account.setMinuteMax(30);
        return account;
    }
}
