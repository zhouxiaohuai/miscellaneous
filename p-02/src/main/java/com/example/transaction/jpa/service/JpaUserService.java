package com.example.transaction.jpa.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA 事务基础演示
 *
 * 演示 @Transactional 的基本用法：
 * 1. 正常提交事务
 * 2. 异常回滚事务
 * 3. 只读事务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JpaUserService {

    private final UserRepository userRepository;

    /**
     * 正常保存用户 - 事务正常提交
     */
    @Transactional
    public User saveUser(User user) {
        log.info("[JPA-事务] 开始保存用户: {}", user.getUsername());
        User saved = userRepository.save(user);
        log.info("[JPA-事务] 用户保存成功, id: {}", saved.getId());
        return saved;
    }

    /**
     * 演示事务回滚 - 保存后抛出异常，事务回滚
     */
    @Transactional
    public User saveUserWithRollback(User user) {
        log.info("[JPA-事务] 开始保存用户: {}", user.getUsername());
        User saved = userRepository.save(user);
        log.info("[JPA-事务] 用户已保存(id:{})，准备抛出异常触发回滚...", saved.getId());

        // 故意抛出异常，触发事务回滚
        throw new RuntimeException("模拟异常，触发事务回滚");
    }

    /**
     * 演示只读事务 - 优化查询性能
     */
    @Transactional(readOnly = true)
    public List<User> findAllReadOnly() {
        log.info("[JPA-只读事务] 查询所有用户");
        return userRepository.findAll();
    }

    /**
     * 根据ID查找用户
     */
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    /**
     * 更新用户状态
     */
    @Transactional
    public int updateStatus(Long id, Integer status) {
        log.info("[JPA-事务] 更新用户状态, id: {}, status: {}", id, status);
        return userRepository.updateStatus(id, status);
    }
}
