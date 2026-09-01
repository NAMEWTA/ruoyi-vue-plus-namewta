package org.dromara.profile.shared.material;

public interface ProfileMaterialAccessPolicy {

    void requireCatalogManage();

    void requireCatalogRead();

    void requireWrite(MaterialOwner owner);

    void requireRead(MaterialOwner owner);
}
