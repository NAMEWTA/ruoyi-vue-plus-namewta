package org.dromara.profile.enterprise.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.enterprise.usecase.ProfileMaterialUseCase;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;
import org.dromara.system.api.OssService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * EnterpriseMaterialAdminController HTTP 接口，负责参数校验和响应包装。
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/profile/enterprise/materials")
public class EnterpriseMaterialAdminController {

    private final ProfileMaterialUseCase materialPort;

    /**
     * 处理 list HTTP 请求。
     */
    @GetMapping("/{ownerType}/{ownerId}")
    @SaCheckPermission(value = {"profile:enterprise:material", "profile:enterprise:query",
        "profile:enterprise:review", "profile:enterprise:manage", "profile:enterprise:override"}, mode = SaMode.OR)
    public R<List<MaterialReferenceView>> list(@PathVariable MaterialOwnerType ownerType,
                                               @Positive @PathVariable Long ownerId) {
        return R.ok(materialPort.list(owner(ownerType, ownerId)));
    }

    /**
     * 处理 accessUrl HTTP 请求。
     */
    @GetMapping("/{ownerType}/{ownerId}/{materialRefId}/access-url")
    @SaCheckPermission(value = {"profile:enterprise:material", "profile:enterprise:query",
        "profile:enterprise:review", "profile:enterprise:manage", "profile:enterprise:override"}, mode = SaMode.OR)
    public R<OssService.OssAccessUrl> accessUrl(@PathVariable MaterialOwnerType ownerType,
                                                @Positive @PathVariable Long ownerId,
                                                @Positive @PathVariable Long materialRefId) {
        return R.ok(materialPort.accessUrl(owner(ownerType, ownerId), materialRefId));
    }

    /**
     * 处理 profileType HTTP 请求。
     */
    ProfileType profileType() {
        return ProfileType.ENTERPRISE;
    }

    /**
     * 处理 owner HTTP 请求。
     */
    private MaterialOwnerKey owner(MaterialOwnerType ownerType, Long ownerId) {
        return new MaterialOwnerKey(profileType(), ownerType, ownerId);
    }
}
