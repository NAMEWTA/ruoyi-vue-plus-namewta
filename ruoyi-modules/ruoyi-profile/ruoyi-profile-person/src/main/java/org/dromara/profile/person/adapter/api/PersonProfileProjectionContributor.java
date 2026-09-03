package org.dromara.profile.person.adapter.api;
import org.dromara.profile.api.ProfileProjectionContributor;
import org.dromara.profile.api.domain.ProfileBindingSummary;
import org.dromara.profile.api.domain.ProfileType;
import org.dromara.profile.person.usecase.PersonProfileApiUseCase;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;
/**
 * 创建个人档案投影处理器。
 */
@Component
public class PersonProfileProjectionContributor implements ProfileProjectionContributor {
    private final PersonProfileApiUseCase useCase;
    /**
     * 处理personprofileprojectioncontributor。
     */
    public PersonProfileProjectionContributor(PersonProfileApiUseCase useCase) {
        this.useCase = useCase;
    }
    /**
     * 返回材料所属档案类型
     */
    @Override
    public ProfileType profileType() {
        return ProfileType.PERSON;
    }
    /**
     * 查询生效的绑定关系
     */
    @Override
    public Map<Long, ProfileBindingSummary> findActiveBindings(Set<Long> userIds) {
        return useCase.findActiveBindings(userIds);
    }
}
