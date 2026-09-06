package org.dromara.demo.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 富文本列表摘要。 */
@Data
public class TestRichTextSummaryVo {
    private Long richTextId;
    private String title;
    private Long version;
    private LocalDateTime updateTime;
}
