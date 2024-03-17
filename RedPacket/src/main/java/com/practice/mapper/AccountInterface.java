package com.practice.mapper;

import com.practice.config.RedPacketProperties;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 模拟账户业务接口类
 */
@Component
@Profile("mysql")
public class AccountInterface {
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    @Autowired
    private RedPacketProperties redPacketProperties; // 配置参数类

    public int decreaseBalance(String userId, int amount) {
        return accountMapper.decreaseBalance(userId, amount);
    }

    public void batchIncreaseBalance(Map<String, Integer> resultMap) {
        switch (redPacketProperties.getSettlementSqlBatch()) {
            // 直接遍历结果Map的每个Key-Value，重复执行单个更新的SQL语句
            case NON_BATCHED -> resultMap.forEach(accountMapper::increaseBalance);
            // 使用MyBatis映射xml文件的foreach动态SQL，拼接一批SQL语句，一次性发送，不会进行预编译
            case NON_PREPARED -> accountMapper.batchIncreaseBalance(resultMap);
            // 使用MyBatis封装的JDBC批处理方式，批量发送SQL语句，会进行预编译
            case PREPARED -> preparedBatchIncreaseBalance(resultMap);
            default -> throw new IllegalArgumentException("无法识别的SQL批量发送方式");
        }
    }

    private void preparedBatchIncreaseBalance(Map<String, Integer> resultMap) {
        try (SqlSession sqlSession =
                    // 获取SqlSession类对象，指定Executor为批量执行类型，并取消自动提交
                     sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            AccountMapper mapper = sqlSession.getMapper(AccountMapper.class);
            resultMap.forEach(mapper::increaseBalance);
            sqlSession.commit();
        }
    }
}
