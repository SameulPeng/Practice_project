package com.practice.common.pojo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户信息封装类，包括用户名和密码
 */
@Setter
@Getter
@ToString
public class Account {
    private String username; // 用户名
    private String password; // 明文密码
}
