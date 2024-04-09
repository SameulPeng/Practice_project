package com.practice.controller;

import com.practice.common.logging.ExtLogger;
import com.practice.common.pojo.Account;
import com.practice.common.result.RedPacketResult;
import com.practice.common.util.JwtUtil;
import com.practice.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@Profile("biz")
public class LoginController {
    private static final ExtLogger log = ExtLogger.create(LoginController.class); // 日志Logger对象
    @Autowired
    private AccountMapper accountMapper;

    /**
     * 登录
     * @param account 用户名和密码
     */
    @PostMapping("/redpacket/login")
    @SuppressWarnings("rawtypes")
    public RedPacketResult login(@RequestBody Account account) {
        String username = account.getUsername();
        String password = account.getPassword();
        // 将明文密码转换为MD5加密的密文密码
        password = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        // 查询数据库，校验账户密码是否正确
        String userId = accountMapper.checkAccount(username, password);
        if (userId != null) {
            log.biz("[ ] [用户 {}] 登录成功", userId);
            // 如果数据库中有用户名和密码记录，则根据对应的用户ID生成JWT令牌
            return RedPacketResult.loginSuccess(JwtUtil.generate(Map.of("userId", userId)));
        } else {
            return RedPacketResult.loginFail();
        }
    }

    /**
     * 登出
     */
    @GetMapping("/redpacket/logout")
    @SuppressWarnings("rawtypes")
    public RedPacketResult logout() {
        return RedPacketResult.logout();
    }
}
