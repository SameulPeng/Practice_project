package com.practice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.sql.DataTruncation;

/**
 * 模拟账户业务接口类
 */
@Mapper
public interface AccountMapper {
    @Select("select balance from account where user_id = #{userId};")
    Long getBalance(String userId);

    @Update("update account set balance = balance - #{amount} where user_id = #{userId};")
    int decreaseBalance(String userId, int amount);

    @Update("update account set balance = balance + #{amount} where user_id = #{userId};")
    void increaseBalance(String userId, int amount);
}
