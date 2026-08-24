package org.dromara.test.notify.monitor;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.notify.event.NotifyDeliveryEvent;
import org.dromara.common.notify.model.*;
import org.dromara.system.api.OssService;
import org.dromara.system.notify.domain.bo.SysNotifyQuery;
import org.dromara.system.notify.mapper.SysNotifyDeliveryLogMapper;
import org.dromara.system.notify.mapper.SysNotifyLogMapper;
import org.dromara.system.notify.service.impl.SysNotifyMonitorServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 一次性 MySQL 上的通知监控 Mapper 与服务合同验证。
 */
@Tag("dev")
class NotifyMonitorMySqlIntegrationTest {

    @Test
    void roundTripsEventGlobalQueryDetailDuplicateAndPhysicalDelete() throws Exception {
        String url = System.getProperty("notify.mysql.integration.url");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "需要一次性 MySQL JDBC URL");
        String username = System.getProperty("notify.mysql.integration.username", "root");
        String password = System.getProperty("notify.mysql.integration.password", "");
        PooledDataSource dataSource = new PooledDataSource("com.mysql.cj.jdbc.Driver", url, username, password);
        prepareSchema(dataSource);

        try {
            SqlSessionFactory factory = sqlSessionFactory(dataSource);
            try (SqlSession session = factory.openSession(true)) {
                SysNotifyLogMapper logMapper = session.getMapper(SysNotifyLogMapper.class);
                SysNotifyDeliveryLogMapper deliveryMapper = session.getMapper(SysNotifyDeliveryLogMapper.class);
                SysNotifyMonitorServiceImpl service = new SysNotifyMonitorServiceImpl(
                    logMapper, deliveryMapper, mock(OssService.class));

                service.record(acceptedEvent(900L, "request-mysql-1"));
                service.record(duplicateEvent(901L, "request-mysql-2", "request-mysql-1"));

                var page = service.page(new SysNotifyQuery(), new PageQuery(10, 1));
                assertEquals(2, page.getTotal());
                assertEquals(List.of("138****5678"), page.getRows().stream()
                    .filter(row -> row.getNotifyLogId().equals(900L))
                    .findFirst().orElseThrow().getMaskedTargets());
                assertTrue(page.getRows().stream()
                    .filter(row -> row.getNotifyLogId().equals(901L))
                    .findFirst().orElseThrow().getMaskedTargets().isEmpty());

                var detail = service.detail(900L);
                assertEquals("验证码 123456", detail.getNotification().getContent());
                assertEquals("13812345678", detail.getDeliveries().getFirst().getTargetValue());
                assertEquals(9L, detail.getNotification().getClientPk());

                assertEquals(2, service.remove(List.of(900L, 901L)));
                assertNull(logMapper.selectById(900L));
                assertEquals(0, deliveryMapper.selectCount(null));
            }
            assertEquals(0, countRows(dataSource, "sys_notify_log"));
            assertEquals(0, countRows(dataSource, "sys_notify_delivery_log"));
        } finally {
            dropSchema(dataSource);
            dataSource.forceCloseAll();
        }
    }

    private NotifyDeliveryEvent acceptedEvent(Long notifyLogId, String requestId) {
        NotifyTarget target = NotifyTarget.phone("13812345678");
        NotifyRequest request = NotifyRequest.builder()
            .requestId(requestId)
            .bizType("contract")
            .bizId("100")
            .channel(NotifyChannel.SMS)
            .targets(List.of(target))
            .content(new NotifyTemplateContent(null, "SMS_001", java.util.Map.of("code", "123456"),
                "验证码 123456"))
            .build();
        NotifyResult result = new NotifyResult(requestId, NotifyChannel.SMS, "sms-main", NotifyStatus.ACCEPTED,
            List.of(NotifyTargetResult.accepted(target, "provider-message-1", 12L)));
        return new NotifyDeliveryEvent(request, new NotifyContext(7L, 9L, "trace-mysql"), result,
            null, notifyLogId, List.of(), Instant.parse("2026-08-22T08:00:00Z"));
    }

    private NotifyDeliveryEvent duplicateEvent(Long notifyLogId, String requestId, String originalRequestId) {
        NotifyRequest request = NotifyRequest.builder()
            .requestId(requestId)
            .channel(NotifyChannel.SMS)
            .targets(List.of(NotifyTarget.phone("13812345678")))
            .content(new NotifyTextContent(null, "验证码 123456"))
            .build();
        NotifyResult result = new NotifyResult(requestId, NotifyChannel.SMS, "sms-main",
            NotifyStatus.SKIPPED_DUPLICATE, List.of());
        return new NotifyDeliveryEvent(request, NotifyContext.empty(), result,
            originalRequestId, notifyLogId, List.of(), Instant.now());
    }

    private SqlSessionFactory sqlSessionFactory(PooledDataSource dataSource) throws Exception {
        Environment environment = new Environment("notify-monitor-test", new JdbcTransactionFactory(), dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setBanner(false);
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(SysNotifyLogMapper.class);
        configuration.addMapper(SysNotifyDeliveryLogMapper.class);
        loadMapperXml(configuration, "mapper/system/SysNotifyLogMapper.xml");
        loadMapperXml(configuration, "mapper/system/SysNotifyDeliveryLogMapper.xml");
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private void loadMapperXml(MybatisConfiguration configuration, String resource) throws Exception {
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    private void prepareSchema(PooledDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists sys_notify_delivery_log");
            statement.execute("drop table if exists sys_notify_log");
            String ddl = Files.readString(repositoryRoot().resolve("script/sql/namewta/DDL.sql"));
            statement.execute(createTable(ddl, "sys_notify_log"));
            statement.execute(createTable(ddl, "sys_notify_delivery_log"));
        }
    }

    private void dropSchema(PooledDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists sys_notify_delivery_log");
            statement.execute("drop table if exists sys_notify_log");
        }
    }

    private long countRows(PooledDataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             var result = statement.executeQuery("select count(*) from " + table)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private String createTable(String ddl, String tableName) {
        String marker = "create table " + tableName + " (";
        int start = ddl.indexOf(marker);
        int end = ddl.indexOf(";", start);
        assertTrue(start >= 0 && end > start, () -> "missing DDL for " + tableName);
        return ddl.substring(start, end);
    }

    private Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir"));
        return current.getFileName().toString().equals("ruoyi-admin") ? current.getParent() : current;
    }
}
