package com.comicdrama.auth.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户信息（脱敏，不含密码）
 */
@Data
public class UserInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer gender;
    private Integer status;
}
