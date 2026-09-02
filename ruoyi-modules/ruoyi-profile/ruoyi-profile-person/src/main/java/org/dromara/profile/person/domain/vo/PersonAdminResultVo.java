package org.dromara.profile.person.domain.vo;

public record PersonAdminResultVo(String status, long profileId, Long versionId, Long bindingId, int version) {
}
