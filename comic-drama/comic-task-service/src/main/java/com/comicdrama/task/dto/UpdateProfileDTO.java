package com.comicdrama.task.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UpdateProfileDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String nickname;

    private String email;

    private String phone;

    private Integer gender;
}
