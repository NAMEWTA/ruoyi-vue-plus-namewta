package org.dromara.profile.shared.material;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
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
import org.dromara.system.api.OssService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/person/materials")
public class PersonMaterialController {

    private final ProfileMaterialPort materialPort;

    @GetMapping("/{ownerType}/{ownerId}")
    @SaCheckPermission(value = {"profile:person:material", "profile:person:query",
        "profile:person:review", "profile:person:manage", "profile:person:override"}, mode = SaMode.OR)
    public R<List<MaterialReferenceView>> list(@PathVariable MaterialOwnerType ownerType,
                                               @PathVariable Long ownerId) {
        return R.ok(materialPort.list(owner(ownerType, ownerId)));
    }

    @PostMapping("/{ownerType}/{ownerId}")
    @SaCheckPermission("profile:person:material")
    @Log(title = "挂接个人认证材料", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<MaterialReferenceView> attach(@PathVariable MaterialOwnerType ownerType,
                                           @PathVariable Long ownerId,
                                           @RequestBody AttachRequest request) {
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

    @GetMapping("/{ownerType}/{ownerId}/{materialRefId}/access-url")
    @SaCheckPermission(value = {"profile:person:material", "profile:person:query",
        "profile:person:review", "profile:person:manage", "profile:person:override"}, mode = SaMode.OR)
    public R<OssService.OssAccessUrl> accessUrl(@PathVariable MaterialOwnerType ownerType,
                                                @PathVariable Long ownerId,
                                                @PathVariable Long materialRefId) {
        return R.ok(materialPort.accessUrl(owner(ownerType, ownerId), materialRefId));
    }

    private MaterialOwnerKey owner(MaterialOwnerType ownerType, Long ownerId) {
        return new MaterialOwnerKey(ProfileType.PERSON, ownerType, ownerId);
    }

    public record AttachRequest(Long ossId, Long materialNodeId) {
    }
}
