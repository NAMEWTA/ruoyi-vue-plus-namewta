package org.dromara.demo.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.demo.domain.TestRichText;
import org.dromara.demo.domain.vo.TestRichTextSummaryVo;

import java.util.List;

/** 富文本演示 Mapper。 */
public interface TestRichTextMapper extends BaseMapperPlus<TestRichText, TestRichTextSummaryVo> {
    default List<TestRichText> selectOwned(Long userId, Long clientPk) {
        return selectList(new LambdaQueryWrapper<TestRichText>()
            .eq(TestRichText::getCreateBy, userId)
            .eq(TestRichText::getClientPk, clientPk)
            .orderByDesc(TestRichText::getUpdateTime));
    }
}
