package org.dromara.notify.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.dromara.notify.domain.bo.NotifyNoticeBo;
import org.dromara.notify.domain.vo.NotifyNoticeVo;
import org.dromara.notify.usecase.NotifyNoticeUseCase;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


/**
 * 通知中心公告管理接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/notify/notice")
public class NotifyNoticeController extends BaseController {
    private final NotifyNoticeUseCase noticeUseCase;

    /** 公告分页。 */
    @SaCheckPermission("notify:notice:list")
    @GetMapping("/list")
    public R<PageResult<NotifyNoticeVo>> list(NotifyNoticeBo query, PageQuery pageQuery) {
        return R.ok(noticeUseCase.list(query, pageQuery.getPageNum(), pageQuery.getPageSize()));
    }

    /** 公告详情。 */
    @SaCheckPermission("notify:notice:query")
    @GetMapping("/{noticeId}")
    public R<NotifyNoticeVo> get(@PathVariable Long noticeId) { return R.ok(noticeUseCase.get(noticeId)); }

    /** 新增或修改公告草稿。 */
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @PostMapping("/save")
    public R<Void> save(@Valid @RequestBody NotifyNoticeBo bo) {
        StpUtil.checkPermission(bo.getNoticeId() == null ? "notify:notice:add" : "notify:notice:edit");
        return toAjax(noticeUseCase.save(bo));
    }

    /** 发布公告并提交统一通知。 */
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @SaCheckPermission("notify:notice:publish")
    @PostMapping("/{noticeId}/publish")
    public R<Void> publish(@PathVariable Long noticeId) {
        noticeUseCase.publish(noticeId);
        return R.ok();
    }

    /** 撤回公告。 */
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @SaCheckPermission("notify:notice:retract")
    @PostMapping("/{noticeId}/retract")
    public R<Void> retract(@PathVariable Long noticeId) { return toAjax(noticeUseCase.retract(noticeId)); }

    /** 删除公告。 */
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @SaCheckPermission("notify:notice:remove")
    @PostMapping("/remove")
    public R<Void> remove(@RequestBody Long[] noticeIds) { return toAjax(noticeUseCase.remove(noticeIds)); }
}
