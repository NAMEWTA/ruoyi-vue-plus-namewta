package org.dromara.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.validate.QueryGroup;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.api.OssService;
import org.dromara.system.domain.bo.SysOssBo;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 文件上传 控制层
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/resource/oss")
public class SysOssController extends BaseController {

    private final ISysOssService ossService;

    private final OssService publicOssService;

    /**
     * 分页查询 OSS 对象存储列表。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return OSS 分页结果
     */
    @SaCheckPermission("system:oss:list")
    @GetMapping("/list")
    public R<PageResult<SysOssVo>> list(@Validated(QueryGroup.class) SysOssBo bo, PageQuery pageQuery) {
        return R.ok(ossService.queryPageList(bo, pageQuery));
    }

    /**
     * 查询OSS对象基于id串
     *
     * @param ossIds OSS对象ID串
     * @return OSS 对象列表
     */
    @SaCheckPermission("system:oss:query")
    @GetMapping("/listByIds/{ossIds}")
    public R<List<SysOssVo>> listByIds(@NotEmpty(message = "主键不能为空")
                                       @PathVariable Long[] ossIds) {
        List<SysOssVo> list = ossService.listByIds(Arrays.asList(ossIds));
        return R.ok(list);
    }

    /**
     * 生成管理面短时下载授权。普通业务应先校验自身业务权限，再调用内部 OssService。
     */
    @SaCheckPermission("system:oss:download")
    @GetMapping("/{ossId}/download-url")
    public R<OssService.OssDownloadUrl> downloadUrl(@PathVariable Long ossId) {
        return R.ok(publicOssService.presignDownload(ossId));
    }

    /**
     * 删除OSS对象存储
     *
     * @param ossIds OSS对象ID串
     * @return 操作结果
     */
    @SaCheckPermission("system:oss:remove")
    @Log(title = "OSS对象存储", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ossIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空")
                          @PathVariable Long[] ossIds) {
        return toAjax(ossService.deleteWithValidByIds(List.of(ossIds), true));
    }
}
