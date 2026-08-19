package org.dromara.web.domain.vo;

import lombok.Data;

/**
 * 客户端公开认证上下文，仅返回前端展示注册入口所需字段。
 *
 * @author NAMEWTA
 */
@Data
public class AuthClientContextVo {

    /**
     * 客户端是否可用
     */
    private Boolean clientEnabled;

    /**
     * 是否开放公开注册
     */
    private Boolean registerEnabled;

}
