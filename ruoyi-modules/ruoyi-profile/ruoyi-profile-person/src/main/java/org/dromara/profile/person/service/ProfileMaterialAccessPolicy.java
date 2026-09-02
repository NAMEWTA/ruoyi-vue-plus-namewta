package org.dromara.profile.person.service;

import org.dromara.profile.person.domain.material.MaterialOwner;
public interface ProfileMaterialAccessPolicy {

    void requireCatalogManage();

    void requireCatalogRead();

    Long requireAttach(MaterialOwner owner);

    void requireWrite(MaterialOwner owner);

    void requireRead(MaterialOwner owner);
}
