package com.practice.controller;

import com.practice.common.logging.ExtLogger;
import com.practice.common.pojo.Account;
import com.practice.common.result.LoginResult;
import com.practice.common.util.JwtUtil;
import com.practice.common.util.PasswordUtil;
import com.practice.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile({"biz-dev", "biz-test" ,"biz-prod"})
public class LoginController {
    private static final ExtLogger log = ExtLogger.create(LoginController.class); // 日志Logger对象
    private AccountMapper accountMapper;

    @Autowired
    private void setAccountMapper(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * 登录
     * @param account 用户名和密码
     */
    @PostMapping("/redpacket/login")
    public LoginResult login(@RequestBody Account account) {
        String username = account.getUsername();
        String password = account.getPassword();
        // 将明文密码转换为密文密码
        password = PasswordUtil.encode(password);
        // 查询数据库，校验账户密码是否正确
        String userId = accountMapper.checkAccount(username, password);
        if (userId != null) {
            log.biz("[ ] [用户 {}] 登录成功", userId);
            // 如果数据库中有用户名和密码记录，则根据对应的用户ID生成JWT令牌
            return LoginResult.loginSuccess(JwtUtil.generate(Map.of("userId", userId)));
        } else {
            return LoginResult.loginFail();
        }
    }

    /**
     * 登出
     */
    @GetMapping("/redpacket/logout")
    public LoginResult logout() {
        return LoginResult.logout();
    }
}
