package com.example.transaction.framework;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 事务上下文
 *
 * 通过 ApplicationContextAware 获取 Spring 容器中的 PlatformTransactionManager
 * 供 Tx 和 TxBuilder 静态方法使用
 */
@Slf4j
@Component
public class TxContext implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    private static PlatformTransactionManager transactionManager;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        TxContext.applicationContext = ctx;
        log.info("[TxContext] ApplicationContext 已注入");
    }

    /**
     * 获取事务管理器
     */
    public static PlatformTransactionManager getTransactionManager() {
        if (transactionManager == null) {
            transactionManager = applicationContext.getBean(PlatformTransactionManager.class);
            log.info("[TxContext] PlatformTransactionManager 已获取: {}", transactionManager.getClass().getSimpleName());
        }
        return transactionManager;
    }

    /**
     * 获取 ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
