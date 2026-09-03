package org.dromara.profile.person.service.impl;

import org.dromara.profile.person.dao.ProfileMaterialDao;
import org.dromara.profile.person.mapper.ProfileMaterialMapper;
import org.dromara.profile.person.port.security.ProfileMaterialAccessPolicy;
import org.dromara.profile.person.service.ProfileMaterialService;
import org.dromara.profile.person.usecase.ProfileMaterialUseCase;
import org.dromara.profile.api.material.ProfileMaterialOwnerContributor;
import org.dromara.profile.api.material.ProfileMaterialPort;
import org.dromara.system.api.OssService;

import java.time.Clock;
import java.util.List;

public class ProfileMaterialServiceImpl extends ProfileMaterialService
    implements ProfileMaterialUseCase, ProfileMaterialPort {
    public ProfileMaterialServiceImpl(ProfileMaterialMapper mapper, OssService ossService,
                                      ProfileMaterialAccessPolicy accessPolicy,
                                      List<ProfileMaterialOwnerContributor> ownerContributors, Clock clock) {
        super(new ProfileMaterialDao(mapper), ossService, accessPolicy, ownerContributors, clock);
    }

    public ProfileMaterialServiceImpl(ProfileMaterialMapper mapper, OssService ossService,
                                      ProfileMaterialAccessPolicy accessPolicy,
                                      List<ProfileMaterialOwnerContributor> ownerContributors) {
        this(mapper, ossService, accessPolicy, ownerContributors, Clock.systemUTC());
    }
}
