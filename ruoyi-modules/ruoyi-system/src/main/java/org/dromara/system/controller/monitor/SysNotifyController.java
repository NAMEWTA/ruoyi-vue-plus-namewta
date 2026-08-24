package org.dromara.system.controller.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.lock.annotation.Lock4j;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.api.OssService;
import org.dromara.system.notify.domain.bo.SysNotifyQuery;
import org.dromara.system.notify.domain.vo.SysNotifyDetailVo;
import org.dromara.system.notify.domain.vo.SysNotifyListVo;
import org.dromara.system.notify.service.ISysNotifyMonitorService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

/**
 * 全局通知监控管理入口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/monitor/notify")
public class SysNotifyController extends BaseController {

    private final ISysNotifyMonitorService monitorService;

    @SaCheckPermission("system:notify:list")
    @GetMapping("/list")
    public R<PageResult<SysNotifyListVo>> list(SysNotifyQuery query, PageQuery pageQuery) {
        return R.ok(monitorService.page(query, pageQuery));
    }

    @SaCheckPermission("system:notify:query")
    @GetMapping("/{notifyLogId}")
    public R<SysNotifyDetailVo> detail(@PathVariable Long notifyLogId) {
        return R.ok(monitorService.detail(notifyLogId));
    }

    @SaCheckPermission("system:notify:query")
    @GetMapping("/{notifyLogId}/attachments/{ossId}/download-url")
    public R<OssService.OssDownloadUrl> attachmentDownload(@PathVariable Long notifyLogId,
                                                            @PathVariable Long ossId) {
        return R.ok(monitorService.attachmentDownload(notifyLogId, ossId));
    }

    @Log(title = "通知监控", businessType = BusinessType.DELETE)
    @SaCheckPermission("system:notify:remove")
    @DeleteMapping("/{notifyLogIds}")
    public R<Void> remove(@NotEmpty @PathVariable Long[] notifyLogIds) {
        return toAjax(monitorService.remove(Arrays.asList(notifyLogIds)));
    }

    @Log(title = "通知监控", businessType = BusinessType.CLEAN)
    @SaCheckPermission("system:notify:remove")
    @Lock4j
    @DeleteMapping("/clean")
    public R<Void> clean() {
        monitorService.clean();
        return R.ok();
    }
}
