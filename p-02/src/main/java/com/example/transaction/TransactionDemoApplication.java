package com.example.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Java 数据库事务全过程研究
 *
 * 启动后访问: http://localhost:8080
 *
 * 知识点覆盖：
 * 1. 事务生命周期全过程
 * 2. 7种传播行为
 * 3. 4种隔离级别
 * 4. 12种事务失效场景 ★
 * 5. 编程式事务
 * 6. 嵌套事务与Savepoint
 * 7. 只读事务
 * 8. 超时事务
 * 9. 批量操作事务优化
 * 10. MyBatis vs JPA 事务对比
 */
@SpringBootApplication
public class TransactionDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionDemoApplication.class, args);
    }
}
