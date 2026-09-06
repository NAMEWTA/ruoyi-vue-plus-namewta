package org.dromara.demo.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 富文本详情视图。 */
@Data
public class TestRichTextVo {
    private Long richTextId;
    private String title;
    private String html;
    private Long version;
    private LocalDateTime updateTime;
}
