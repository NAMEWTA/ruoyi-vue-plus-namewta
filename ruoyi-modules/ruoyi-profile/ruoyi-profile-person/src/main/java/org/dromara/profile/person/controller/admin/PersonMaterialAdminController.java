package org.dromara.profile.person.controller.admin;

import jakarta.validation.Valid;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerKey;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialOwnerType;
import org.dromara.profile.api.material.ProfileMaterialPort.MaterialReferenceView;
import org.dromara.system.api.OssService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile/person/materials")
public class PersonMaterialAdminController {

    private final ProfileMaterialPort materialPort;

    @GetMapping("/{ownerType}/{ownerId}")
    @SaCheckPermission(value = {"profile:person:material", "profile:person:query",
        "profile:person:review", "profile:person:manage", "profile:person:override"}, mode = SaMode.OR)
    public R<List<MaterialReferenceView>> list(@PathVariable MaterialOwnerType ownerType,
                                               @PathVariable Long ownerId) {
        return R.ok(materialPort.list(owner(ownerType, ownerId)));
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
}
