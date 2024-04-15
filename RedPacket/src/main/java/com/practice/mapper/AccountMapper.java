package com.practice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

/**
 * 模拟账户底层数据库访问
 */
@Mapper
public interface AccountMapper {
    /**
     * 扣减账户余额
     * @param userId 用户ID
     * @param amount 扣减金额
     * @return 受影响的数据库表记录数，正常情况下应当为1
     */
    @Update("update account set balance = balance - #{amount} where user_id = #{userId}")
    int decreaseBalance(String userId, int amount);

    /**
     * 增加账户余额
     * @param userId 用户ID
     * @param amount 增加金额
     */
    @Update("update account set balance = balance + #{amount} where user_id = #{userId}")
    void increaseBalance(String userId, int amount);

    /**
     * 使用MyBatis映射xml文件的foreach动态SQL，批量增加账户余额
     * @param resultMap 整理后的红包结果
     */
    void batchIncreaseBalance(@Param("resultMap") Map<String, Integer> resultMap);

    /**
     * 检查用户名和密码是否正确，如果是则返回用户ID
     * @param username 用户名
     * @param password 密文密码
     * @return 用户ID
     */
    @Select("select user_id from account where username = #{username} and password = #{password}")
    String checkAccount(String username, String password);
}
