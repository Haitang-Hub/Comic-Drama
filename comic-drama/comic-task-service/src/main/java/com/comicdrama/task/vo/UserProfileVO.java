package com.comicdrama.task.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserProfileVO implements Serializable {

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

    private String role;
}
