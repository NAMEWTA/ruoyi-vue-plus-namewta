package org.dromara.profile.shared.material;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialNodeView;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/material-tags")
public class MaterialTagController {

    private final ProfileMaterialPort materialPort;

    @GetMapping("/tree")
    @SaCheckPermission(value = {"profile:material-tag:query", "profile:person:material",
        "profile:enterprise:material"}, mode = SaMode.OR)
    public R<List<MaterialNodeView>> tree(@RequestParam MaterialScope scope,
                                          @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return R.ok(materialPort.tree(scope, includeDisabled));
    }

    @PostMapping
    @SaCheckPermission("profile:material-tag:manage")
    @Log(title = "新增档案材料节点", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<MaterialNodeView> create(@RequestBody MaterialNodeCommand command) {
        return R.ok(materialPort.createNode(command));
    }

    @PostMapping("/{materialNodeId}")
    @SaCheckPermission("profile:material-tag:manage")
    @Log(title = "修改档案材料节点", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<MaterialNodeView> update(@PathVariable Long materialNodeId,
                                      @RequestBody MaterialNodeCommand command) {
        return R.ok(materialPort.updateNode(materialNodeId, command));
    }

    @PostMapping("/{materialNodeId}/status")
    @SaCheckPermission("profile:material-tag:manage")
    @Log(title = "变更档案材料节点状态", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Void> status(@PathVariable Long materialNodeId, @RequestBody StatusCommand command) {
        materialPort.changeStatus(materialNodeId, command.enabled(), command.expectedVersion());
        return R.ok();
    }

    @PostMapping("/{materialNodeId}/archive")
    @SaCheckPermission("profile:material-tag:manage")
    @Log(title = "归档档案材料节点", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Void> archive(@PathVariable Long materialNodeId, @RequestBody VersionCommand command) {
        materialPort.archiveNode(materialNodeId, command.expectedVersion());
        return R.ok();
    }

    public record StatusCommand(boolean enabled, int expectedVersion) {
    }

    public record VersionCommand(int expectedVersion) {
    }
}
