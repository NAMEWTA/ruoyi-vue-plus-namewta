package org.dromara.system.notify.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.notify.event.NotifyDeliveryEvent;
import org.dromara.common.notify.model.*;
import org.dromara.system.api.OssService;
import org.dromara.system.notify.domain.SysNotifyDeliveryLog;
import org.dromara.system.notify.domain.SysNotifyLog;
import org.dromara.system.notify.domain.bo.SysNotifyQuery;
import org.dromara.system.notify.domain.vo.SysNotifyDetailVo;
import org.dromara.system.notify.domain.vo.SysNotifyListVo;
import org.dromara.system.notify.mapper.SysNotifyDeliveryLogMapper;
import org.dromara.system.notify.mapper.SysNotifyLogMapper;
import org.dromara.system.notify.service.ISysNotifyMonitorService;
import org.dromara.system.notify.support.NotifyLogSanitizer;
import org.dromara.system.notify.support.NotifyTargetMasker;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知监控服务。所有查询都是平台全局查询，clientPk 只接受显式筛选。
 */
@Service
@RequiredArgsConstructor
public class SysNotifyMonitorServiceImpl implements ISysNotifyMonitorService {

    private static final String NOTIFY_LOG_TABLE = "sys_notify_log";
    private static final long SYSTEM_USER_ID = -1L;
    private static final int CLEANUP_BATCH_SIZE = 500;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SysNotifyLogMapper logMapper;
    private final SysNotifyDeliveryLogMapper deliveryMapper;
    private final OssService ossService;

    @Override
    @DSTransactional
    public void record(NotifyDeliveryEvent event) {
        Objects.requireNonNull(event, "通知事件不能为空");
        NotifyRequest request = Objects.requireNonNull(event.request(), "通知请求快照不能为空");
        NotifyResult result = Objects.requireNonNull(event.result(), "通知结果快照不能为空");
        NotifyContext context = event.context() == null ? NotifyContext.empty() : event.context();
        LocalDateTime occurredAt = event.occurredAt() == null
            ? LocalDateTime.now() : LocalDateTime.ofInstant(event.occurredAt(), ZoneId.systemDefault());
        if (!event.attachmentSnapshotOssIds().isEmpty() && event.notifyLogId() == null) {
            throw new IllegalArgumentException("附件快照事件必须携带预生成的通知日志主键");
        }

        SysNotifyLog log = buildLog(event, request, result, context, occurredAt);
        logMapper.insert(log);
        Long notifyLogId = Objects.requireNonNull(log.getNotifyLogId(), "通知日志主键不能为空");

        if (result.status() != NotifyStatus.SKIPPED_DUPLICATE) {
            for (NotifyTargetResult targetResult : result.deliveries()) {
                deliveryMapper.insert(buildDelivery(notifyLogId, result.providerKey(), targetResult,
                    request.auditPolicy(), context.userId(), occurredAt));
            }
        }
        ossService.reconcileReferences(NOTIFY_LOG_TABLE, String.valueOf(notifyLogId),
            List.of(), event.attachmentSnapshotOssIds());
    }

    @Override
    public PageResult<SysNotifyListVo> page(SysNotifyQuery query, PageQuery pageQuery) {
        SysNotifyQuery criteria = query == null ? new SysNotifyQuery() : query;
        Set<Long> providerMatches = providerMatches(criteria.getProviderMessageId());
        if (providerMatches != null && providerMatches.isEmpty()) {
            return PageResult.build(List.of(), 0);
        }
        LambdaQueryWrapper<SysNotifyLog> wrapper = new LambdaQueryWrapper<SysNotifyLog>()
            .eq(StringUtils.isNotBlank(criteria.getRequestId()), SysNotifyLog::getRequestId, criteria.getRequestId())
            .eq(StringUtils.isNotBlank(criteria.getOriginalRequestId()), SysNotifyLog::getOriginalRequestId,
                criteria.getOriginalRequestId())
            .eq(StringUtils.isNotBlank(criteria.getBizType()), SysNotifyLog::getBizType, criteria.getBizType())
            .eq(StringUtils.isNotBlank(criteria.getBizId()), SysNotifyLog::getBizId, criteria.getBizId())
            .eq(StringUtils.isNotBlank(criteria.getChannel()), SysNotifyLog::getChannel, criteria.getChannel())
            .eq(StringUtils.isNotBlank(criteria.getProviderKey()), SysNotifyLog::getProviderKey,
                criteria.getProviderKey())
            .eq(StringUtils.isNotBlank(criteria.getStatus()), SysNotifyLog::getStatus, criteria.getStatus())
            .eq(StringUtils.isNotBlank(criteria.getTraceId()), SysNotifyLog::getTraceId, criteria.getTraceId())
            .eq(criteria.getClientPk() != null, SysNotifyLog::getClientPk, criteria.getClientPk())
            .ge(criteria.getBeginTime() != null, SysNotifyLog::getCreateTime, criteria.getBeginTime())
            .le(criteria.getEndTime() != null, SysNotifyLog::getCreateTime, criteria.getEndTime())
            .in(providerMatches != null, SysNotifyLog::getNotifyLogId, providerMatches)
            .orderByDesc(SysNotifyLog::getNotifyLogId);
        Page<SysNotifyLog> page = logMapper.selectPage(pageQuery.build(), wrapper);
        Map<Long, List<SysNotifyDeliveryLog>> deliveries = deliveriesByLogIds(
            page.getRecords().stream().map(SysNotifyLog::getNotifyLogId).toList());
        List<SysNotifyListVo> rows = page.getRecords().stream()
            .map(log -> toListVo(log, deliveries.getOrDefault(log.getNotifyLogId(), List.of())))
            .toList();
        return PageResult.build(rows, page.getTotal());
    }

    @Override
    public SysNotifyDetailVo detail(Long notifyLogId) {
        SysNotifyLog log = logMapper.selectById(notifyLogId);
        if (log == null) {
            throw new ServiceException("通知日志不存在");
        }
        SysNotifyDetailVo detail = new SysNotifyDetailVo();
        detail.setNotification(log);
        detail.setDeliveries(deliveryMapper.selectList(new LambdaQueryWrapper<SysNotifyDeliveryLog>()
            .eq(SysNotifyDeliveryLog::getNotifyLogId, notifyLogId)
            .orderByAsc(SysNotifyDeliveryLog::getNotifyDeliveryLogId)));
        detail.setAttachmentOssIds(parseOssIds(log.getAttachmentOssIds()));
        return detail;
    }

    @Override
    public OssService.OssDownloadUrl attachmentDownload(Long notifyLogId, Long ossId) {
        SysNotifyLog log = logMapper.selectById(notifyLogId);
        if (log == null) {
            throw new ServiceException("通知日志不存在");
        }
        if (!parseOssIds(log.getAttachmentOssIds()).contains(ossId)) {
            throw new ServiceException("附件不属于当前通知");
        }
        return ossService.presignDownload(ossId);
    }

    @Override
    @DSTransactional
    public int remove(Collection<Long> notifyLogIds) {
        if (notifyLogIds == null || notifyLogIds.isEmpty()) {
            return 0;
        }
        List<Long> ids = notifyLogIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) {
            return 0;
        }
        List<SysNotifyLog> logs = logMapper.selectBatchIds(ids).stream()
            .sorted(Comparator.comparing(SysNotifyLog::getNotifyLogId)).toList();
        deliveryMapper.physicalDeleteByNotifyLogIds(ids);
        int rows = logMapper.physicalDeleteByIds(ids);
        if (rows > 0) {
            for (SysNotifyLog log : logs) {
                ossService.reconcileReferences(NOTIFY_LOG_TABLE, String.valueOf(log.getNotifyLogId()),
                    parseOssIds(log.getAttachmentOssIds()), List.of());
            }
        }
        return rows;
    }

    @Override
    @DSTransactional
    public void clean() {
        List<Long> ids;
        do {
            ids = logMapper.selectCleanupBatch(CLEANUP_BATCH_SIZE);
            remove(ids);
        } while (ids.size() == CLEANUP_BATCH_SIZE);
    }

    private SysNotifyLog buildLog(NotifyDeliveryEvent event, NotifyRequest request, NotifyResult result,
                                  NotifyContext context, LocalDateTime occurredAt) {
        SysNotifyLog log = new SysNotifyLog();
        log.setNotifyLogId(event.notifyLogId());
        log.setRequestId(request.requestId());
        log.setOriginalRequestId(event.originalRequestId());
        log.setBizType(request.bizType());
        log.setBizId(request.bizId());
        log.setChannel(result.channel().value());
        log.setProviderKey(result.providerKey());
        if (request.auditPolicy() == NotifyAuditPolicy.FULL) {
            mapContent(log, request.content());
        }
        log.setAttachmentOssIds(JSON.writeValueAsString(event.attachmentSnapshotOssIds()));
        log.setStatus(result.status().name());
        result.deliveries().stream()
            .filter(delivery -> delivery.status() == NotifyDeliveryStatus.FAILED)
            .findFirst()
            .ifPresent(delivery -> {
                log.setErrorCode(delivery.errorCode());
                log.setErrorMessage(NotifyLogSanitizer.error(delivery.errorMessage()));
            });
        log.setClientPk(context.clientPk());
        log.setUserId(context.userId());
        log.setTraceId(context.traceId());
        populateAudit(log, context.userId(), occurredAt);
        return log;
    }

    private void mapContent(SysNotifyLog log, NotifyContent content) {
        if (content == null) {
            return;
        }
        log.setSubject(content.subject());
        log.setContentSnapshot(content.contentSnapshot());
        if (content instanceof NotifyTextContent text) {
            log.setContent(text.text());
            log.setContentType("TEXT");
        } else if (content instanceof NotifyRichContent rich) {
            log.setContent(rich.content());
            log.setContentType(rich.html() ? "HTML" : "TEXT");
        } else if (content instanceof NotifyTemplateContent template) {
            log.setContent(template.contentSnapshot());
            log.setContentType("TEMPLATE");
            log.setTemplateCode(template.providerTemplateCode());
            log.setTemplateParams(JSON.writeValueAsString(template.params()));
        }
    }

    private SysNotifyDeliveryLog buildDelivery(Long notifyLogId, String providerKey,
                                                NotifyTargetResult result, NotifyAuditPolicy auditPolicy, Long userId,
                                                LocalDateTime occurredAt) {
        SysNotifyDeliveryLog delivery = new SysNotifyDeliveryLog();
        delivery.setNotifyLogId(notifyLogId);
        delivery.setTargetType(result.target().type());
        delivery.setTargetRole(result.target().role());
        delivery.setTargetValue(auditPolicy == NotifyAuditPolicy.REDACT_SENSITIVE
            ? NotifyTargetMasker.mask(result.target().type(), result.target().value())
            : result.target().value());
        delivery.setProviderKey(providerKey);
        delivery.setProviderMessageId(result.providerMessageId());
        delivery.setAttemptNo(1);
        delivery.setStatus(result.status().name());
        delivery.setCostTime(result.costTime());
        delivery.setErrorCode(result.errorCode());
        delivery.setErrorMessage(NotifyLogSanitizer.error(result.errorMessage()));
        populateAudit(delivery, userId, occurredAt);
        return delivery;
    }

    private void populateAudit(org.dromara.common.mybatis.core.domain.BaseEntity entity, Long userId,
                               LocalDateTime occurredAt) {
        long auditUserId = userId == null ? SYSTEM_USER_ID : userId;
        entity.setCreateBy(auditUserId);
        entity.setUpdateBy(auditUserId);
        entity.setCreateDept(SYSTEM_USER_ID);
        entity.setCreateTime(occurredAt);
        entity.setUpdateTime(occurredAt);
    }

    private Set<Long> providerMatches(String providerMessageId) {
        if (StringUtils.isBlank(providerMessageId)) {
            return null;
        }
        return deliveryMapper.selectList(new LambdaQueryWrapper<SysNotifyDeliveryLog>()
                .eq(SysNotifyDeliveryLog::getProviderMessageId, providerMessageId))
            .stream().map(SysNotifyDeliveryLog::getNotifyLogId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<Long, List<SysNotifyDeliveryLog>> deliveriesByLogIds(List<Long> notifyLogIds) {
        if (notifyLogIds.isEmpty()) {
            return Map.of();
        }
        return deliveryMapper.selectList(new LambdaQueryWrapper<SysNotifyDeliveryLog>()
                .in(SysNotifyDeliveryLog::getNotifyLogId, notifyLogIds)
                .orderByAsc(SysNotifyDeliveryLog::getNotifyDeliveryLogId))
            .stream().collect(Collectors.groupingBy(SysNotifyDeliveryLog::getNotifyLogId,
                LinkedHashMap::new, Collectors.toList()));
    }

    private SysNotifyListVo toListVo(SysNotifyLog log, List<SysNotifyDeliveryLog> deliveries) {
        SysNotifyListVo vo = BeanUtil.toBean(log, SysNotifyListVo.class);
        vo.setMaskedTargets(deliveries.stream()
            .map(delivery -> NotifyTargetMasker.mask(delivery.getTargetType(), delivery.getTargetValue()))
            .toList());
        return vo;
    }

    private List<Long> parseOssIds(String value) {
        if (StringUtils.isBlank(value)) {
            return List.of();
        }
        return JSON.readValue(value, JSON.getTypeFactory().constructCollectionType(List.class, Long.class));
    }
}
