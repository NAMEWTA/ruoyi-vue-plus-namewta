package org.dromara.profile.enterprise.controller.self;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialAttachCommand;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/profile/enterprise/materials")
public class EnterpriseMaterialSelfController {

    private final ProfileMaterialPort materialPort;

    @PostMapping("/{ownerType}/{ownerId}")
    @SaCheckPermission("profile:enterprise:material")
    @Log(title = "挂接企业认证材料", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<MaterialReferenceView> attach(@PathVariable MaterialOwnerType ownerType,
                                           @Positive @PathVariable Long ownerId,
                                           @Valid @RequestBody AttachRequest request) {
        return R.ok(materialPort.attach(new MaterialAttachCommand(owner(ownerType, ownerId),
            request.ossId(), request.materialNodeId())));
    }

    @PostMapping("/{ownerType}/{ownerId}/{materialRefId}/detach")
    @SaCheckPermission("profile:enterprise:material")
    @Log(title = "解除企业认证材料", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Void> detach(@PathVariable MaterialOwnerType ownerType, @Positive @PathVariable Long ownerId,
                          @Positive @PathVariable Long materialRefId) {
        materialPort.detach(owner(ownerType, ownerId), materialRefId);
        return R.ok();
    }

    ProfileType profileType() {
        return ProfileType.ENTERPRISE;
    }

    private MaterialOwnerKey owner(MaterialOwnerType ownerType, Long ownerId) {
        return new MaterialOwnerKey(profileType(), ownerType, ownerId);
    }

    public record AttachRequest(@Positive Long ossId, @Positive Long materialNodeId) {
    }
}
