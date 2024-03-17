package com.practice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

/**
 * 模拟账户底层数据库访问
 */
@Mapper
public interface AccountMapper {
    @Update("update account set balance = balance - #{amount} where user_id = #{userId}")
    int decreaseBalance(String userId, int amount);

    @Update("update account set balance = balance + #{amount} where user_id = #{userId}")
    void increaseBalance(String userId, int amount);

    void batchIncreaseBalance(@Param("resultMap") Map<String, Integer> resultMap);
}
