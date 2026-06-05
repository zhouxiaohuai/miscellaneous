package com.aichat.auth.repo;

import com.aichat.auth.model.WechatAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WechatAccountRepository extends JpaRepository<WechatAccount, Long> {
    Optional<WechatAccount> findByOpenId(String openId);
}

