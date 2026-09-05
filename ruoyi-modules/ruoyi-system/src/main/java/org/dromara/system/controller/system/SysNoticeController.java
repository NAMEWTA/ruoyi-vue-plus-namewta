package org.dromara.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.service.DictService;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.api.OssService;
import org.dromara.notify.api.NotificationApplicationService;
import org.dromara.notify.api.NotificationChannel;
import org.dromara.notify.api.NotificationCommand;
import org.dromara.notify.api.NotificationMode;
import org.dromara.notify.api.NotificationStrategy;
import org.dromara.system.domain.bo.SysNoticeBo;
import org.dromara.system.domain.vo.SysNoticeVo;
import org.dromara.system.service.ISysNoticeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告 信息操作处理
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/notice")
public class SysNoticeController extends BaseController {

    private final ISysNoticeService noticeService;
    private final DictService dictService;
    private final NotificationApplicationService notificationService;

    /**
     * 分页查询通知公告列表。
     *
     * @param notice    查询条件
     * @param pageQuery 分页参数
     * @return 公告分页结果
     */
    @SaCheckPermission("system:notice:list")
    @GetMapping("/list")
    public R<PageResult<SysNoticeVo>> list(SysNoticeBo notice, PageQuery pageQuery) {
        return R.ok(noticeService.selectPageNoticeList(notice, pageQuery));
    }

    /**
     * 根据通知公告编号获取详细信息
     *
     * @param noticeId 公告ID
     * @return 公告详情
     */
    @SaCheckPermission("system:notice:query")
    @GetMapping(value = "/{noticeId}")
    public R<SysNoticeVo> getInfo(@PathVariable Long noticeId) {
        return R.ok(noticeService.selectNoticeById(noticeId));
    }

    @SaCheckPermission("system:notice:query")
    @GetMapping(value = "/{noticeId}/attachments/download-urls")
    public R<Map<Long, OssService.OssDownloadUrl>> attachmentDownloads(@PathVariable Long noticeId) {
        return R.ok(noticeService.attachmentDownloads(noticeId));
    }

    /**
     * 新增通知公告；只有正常状态的公告才生成发布通知意图。
     *
     * @param notice 公告参数
     * @return 操作结果
     */
    @SaCheckPermission("system:notice:add")
    @Log(title = "通知公告", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysNoticeBo notice) {
        int rows = noticeService.insertNotice(notice);
        if (rows <= 0) {
            return R.fail();
        }
        if (!"0".equals(notice.getStatus())) {
            return R.ok();
        }
        publishNotice(notice);
        return R.ok();
    }

    /**
     * 显式发布公告并生成不可变通知快照。
     *
     * @param noticeId 公告编号
     * @return 操作结果
     */
    @SaCheckPermission("system:notice:publish")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @PostMapping("/{noticeId}/publish")
    public R<Void> publish(@PathVariable Long noticeId) {
        SysNoticeVo notice = noticeService.selectNoticeById(noticeId);
        if (notice == null) {
            return R.fail("公告不存在");
        }
        SysNoticeBo command = new SysNoticeBo();
        command.setNoticeId(noticeId);
        command.setNoticeTitle(notice.getNoticeTitle());
        command.setNoticeType(notice.getNoticeType());
        command.setNoticeContent(notice.getNoticeContent());
        command.setStatus("0");
        if (noticeService.updateNotice(command) <= 0) {
            return R.fail("公告发布失败");
        }
        publishNotice(command);
        return R.ok();
    }

    private void publishNotice(SysNoticeBo notice) {
        String type = dictService.getDictLabel("sys_notice_type", notice.getNoticeType());
        Map<String, Object> data = new HashMap<>(4);
        data.put("noticeType", notice.getNoticeType());
        data.put("noticeTypeLabel", type);
        data.put("noticeTitle", notice.getNoticeTitle());
        data.put("noticeId", notice.getNoticeId());
        data.put("noticeContent", notice.getNoticeContent());
        data.put("status", notice.getStatus());
        notificationService.submit(new NotificationCommand("system", "notice-published", "NOTICE_PUBLISHED",
            String.valueOf(notice.getNoticeId()), "ALL", java.util.List.of(), "notice-published",
            Map.of("title", "[" + type + "] " + notice.getNoticeTitle(),
                "content", notice.getNoticeContent(), "path", "/system/notice?noticeId=" + notice.getNoticeId(),
                "notice", data), List.of(NotificationChannel.IN_APP, NotificationChannel.SMS, NotificationChannel.MAIL),
            NotificationStrategy.ALL, NotificationMode.ASYNC, 50, null, null,
            "notice-published:" + notice.getNoticeId(), Map.of("audit", "NOTICE_SNAPSHOT")));
    }

    /**
     * 修改通知公告。
     *
     * @param notice 公告参数
     * @return 操作结果
     */
    @SaCheckPermission("system:notice:edit")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysNoticeBo notice) {
        return toAjax(noticeService.updateNotice(notice));
    }

    /**
     * 删除通知公告
     *
     * @param noticeIds 公告ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:notice:remove")
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public R<Void> remove(@PathVariable Long[] noticeIds) {
        return toAjax(noticeService.deleteNoticeByIds(noticeIds));
    }
}
