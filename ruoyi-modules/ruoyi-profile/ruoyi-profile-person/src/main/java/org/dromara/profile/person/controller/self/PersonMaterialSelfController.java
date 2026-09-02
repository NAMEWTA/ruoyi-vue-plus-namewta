package org.dromara.profile.person.controller.self;

import jakarta.validation.Valid;

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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/person/materials")
public class PersonMaterialSelfController {

    private final ProfileMaterialPort materialPort;

    @PostMapping("/{ownerType}/{ownerId}")
    @SaCheckPermission("profile:person:material")
    @Log(title = "挂接个人认证材料", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<MaterialReferenceView> attach(@PathVariable MaterialOwnerType ownerType,
                                           @PathVariable Long ownerId,
                                           @Valid @RequestBody AttachRequest request) {
        return R.ok(materialPort.attach(new MaterialAttachCommand(owner(ownerType, ownerId),
            request.ossId(), request.materialNodeId())));
    }

    @PostMapping("/{ownerType}/{ownerId}/{materialRefId}/detach")
    @SaCheckPermission("profile:person:material")
    @Log(title = "解除个人认证材料", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Void> detach(@PathVariable MaterialOwnerType ownerType, @PathVariable Long ownerId,
                          @PathVariable Long materialRefId) {
        materialPort.detach(owner(ownerType, ownerId), materialRefId);
        return R.ok();
    }

    private MaterialOwnerKey owner(MaterialOwnerType ownerType, Long ownerId) {
        return new MaterialOwnerKey(ProfileType.PERSON, ownerType, ownerId);
    }

    public record AttachRequest(Long ossId, Long materialNodeId) {
    }
}
