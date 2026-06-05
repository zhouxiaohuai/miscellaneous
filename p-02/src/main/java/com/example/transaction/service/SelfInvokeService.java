package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自调用辅助服务 - 用于演示事务失效场景2和场景8
 * 将事务方法放在独立的 Service 中，通过注入调用来避免自调用问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfInvokeService {

    private final UserRepository userRepository;

    /**
     * 内部事务方法 - 通过注入调用，避免自调用
     */
    @Transactional
    public void innerTransactionalMethod(User user) {
        log.info("[SelfInvokeService] 保存用户: {}", user.getUsername());
        userRepository.save(user);
        throw new RuntimeException("异常被抛出，事务应该回滚");
    }

    /**
     * NOT_SUPPORTED 传播行为的方法
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notSupportedMethod(User user) {
        log.info("[SelfInvokeService-NOT_SUPPORTED] 非事务方式保存用户: {}", user.getUsername());
        userRepository.save(user);
    }

    /**
     * 普通内部方法（用于多线程场景）
     */
    @Transactional
    public void innerMethod(User user) {
        log.info("[SelfInvokeService] 子线程保存用户: {}", user.getUsername());
        userRepository.save(user);
    }
}
