package com.comicdrama.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 登录返回信息
 */
@Data
@Builder
public class LoginInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tokenName;
    private String tokenValue;
    private UserInfoVO userInfo;
    private List<String> roles;
    private List<String> permissions;
}
