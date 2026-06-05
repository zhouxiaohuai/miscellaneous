package com.example.transaction.framework;

/**
 * 自定义事务异常
 *
 * 当事务执行过程中发生 checked 异常时，包装为 TxException 抛出
 */
public class TxException extends RuntimeException {

    public TxException(String message) {
        super(message);
    }

    public TxException(String message, Throwable cause) {
        super(message, cause);
    }
}
