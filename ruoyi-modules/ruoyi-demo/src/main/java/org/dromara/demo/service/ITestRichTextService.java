package org.dromara.demo.service;

import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.demo.domain.bo.TestRichTextBo;
import org.dromara.demo.domain.vo.TestRichTextAssetVo;
import org.dromara.demo.domain.vo.TestRichTextSummaryVo;
import org.dromara.demo.domain.vo.TestRichTextVo;

import java.util.List;

/** 富文本演示服务。 */
public interface ITestRichTextService {
    PageResult<TestRichTextSummaryVo> list(PageQuery pageQuery);
    TestRichTextVo get(Long id);
    TestRichTextVo create(TestRichTextBo bo);
    TestRichTextVo update(Long id, TestRichTextBo bo);
    void remove(Long id, Long version);
    List<TestRichTextAssetVo> assets(String ossIds, Long richTextId);
}
