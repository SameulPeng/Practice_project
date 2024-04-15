package com.practice.processor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * Bean工厂后处理器类，用于提前加载指定类<br/>
 * 加载失败则抛出异常，使容器无法启动，以实现快速失败（Fail-fast）
 */
@Component
public class StaticClassLoadingProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            // 加载JWT工具类，如果读取配置文件失败或配置项值不合法，则直接抛出异常
            Class.forName("com.practice.common.util.JwtUtil");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
