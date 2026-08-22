package org.dromara.common.mail.config;

import cn.hutool.extra.mail.MailAccount;
import org.dromara.common.mail.config.properties.MailProperties;
import org.dromara.common.mail.core.MailBuilder;
import org.dromara.common.mail.notify.MailNotificationSender;
import org.dromara.common.mail.notify.MailNotifyChannelAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.File;

/**
 * JavaMail 配置
 *
 * @author Michelle.Chung
 */
@AutoConfiguration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /**
     * 创建邮件账户配置。
     *
     * @param mailProperties 邮件配置属性
     * @return 邮件账户
     */
    @Bean
    @ConditionalOnProperty(value = "mail.enabled", havingValue = "true")
    public MailAccount mailAccount(MailProperties mailProperties) {
        return mailProperties.toMailAccount();
    }

    @Bean
    @ConditionalOnBean(MailAccount.class)
    @ConditionalOnMissingBean
    public MailNotificationSender mailNotificationSender() {
        return message -> MailBuilder.of()
            .to(message.to())
            .cc(message.cc())
            .bcc(message.bcc())
            .subject(message.subject())
            .content(message.content(), message.html())
            .files(message.attachments().stream().map(path -> path.toFile()).toArray(File[]::new))
            .send();
    }

    @Bean
    @ConditionalOnBean(MailNotificationSender.class)
    @ConditionalOnMissingBean
    public MailNotifyChannelAdapter mailNotifyChannelAdapter(MailNotificationSender sender) {
        return new MailNotifyChannelAdapter(sender);
    }

}
