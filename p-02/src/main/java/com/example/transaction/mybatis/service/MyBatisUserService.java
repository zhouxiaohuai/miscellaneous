package com.example.transaction.mybatis.service;

import com.example.transaction.entity.User;
import com.example.transaction.mybatis.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MyBatis 事务基础演示
 *
 * 与 JPA 版本对比，展示 MyBatis 下事务的使用方式
 * 注意：MyBatis 本身不管理事务，事务由 Spring 统一管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MyBatisUserService {

    private final UserMapper userMapper;

    /**
     * 正常保存用户 - 事务正常提交
     */
    @Transactional
    public User saveUser(User user) {
        log.info("[MyBatis-事务] 开始保存用户: {}", user.getUsername());
        userMapper.insert(user);
        log.info("[MyBatis-事务] 用户保存成功, id: {}", user.getId());
        return user;
    }

    /**
     * 演示事务回滚 - 保存后抛出异常，事务回滚
     */
    @Transactional
    public User saveUserWithRollback(User user) {
        log.info("[MyBatis-事务] 开始保存用户: {}", user.getUsername());
        userMapper.insert(user);
        log.info("[MyBatis-事务] 用户已保存(id:{})，准备抛出异常触发回滚...", user.getId());

        // 故意抛出异常，触发事务回滚
        throw new RuntimeException("模拟异常，触发事务回滚");
    }

    /**
     * 演示只读事务
     */
    @Transactional(readOnly = true)
    public List<User> findAllReadOnly() {
        log.info("[MyBatis-只读事务] 查询所有用户");
        return userMapper.findAll();
    }

    /**
     * 根据ID查找用户
     */
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userMapper.findById(id);
    }

    /**
     * 更新用户状态
     */
    @Transactional
    public int updateStatus(Long id, Integer status) {
        log.info("[MyBatis-事务] 更新用户状态, id: {}, status: {}", id, status);
        return userMapper.updateStatus(id, status);
    }
}
