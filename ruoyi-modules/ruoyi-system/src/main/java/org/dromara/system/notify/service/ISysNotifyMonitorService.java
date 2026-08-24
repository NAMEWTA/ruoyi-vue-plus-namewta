package org.dromara.system.notify.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.notify.event.NotifyDeliveryEvent;
import org.dromara.system.api.OssService;
import org.dromara.system.notify.domain.bo.SysNotifyQuery;
import org.dromara.system.notify.domain.vo.SysNotifyDetailVo;
import org.dromara.system.notify.domain.vo.SysNotifyListVo;

import java.util.Collection;

/**
 * 通知监控持久化与全局管理服务。
 */
public interface ISysNotifyMonitorService {

    void record(NotifyDeliveryEvent event);

    PageResult<SysNotifyListVo> page(SysNotifyQuery query, PageQuery pageQuery);

    SysNotifyDetailVo detail(Long notifyLogId);

    OssService.OssDownloadUrl attachmentDownload(Long notifyLogId, Long ossId);

    int remove(Collection<Long> notifyLogIds);

    void clean();
}
