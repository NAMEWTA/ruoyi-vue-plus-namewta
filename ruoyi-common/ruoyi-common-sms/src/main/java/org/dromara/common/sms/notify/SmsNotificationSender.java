package org.dromara.common.sms.notify;

import org.dromara.common.notify.model.NotifyContent;

/**
 * 已选定 SMS Provider 的发送接缝。
 */
@FunctionalInterface
public interface SmsNotificationSender {

    SmsNotificationReceipt send(String phone, NotifyContent content);
}
