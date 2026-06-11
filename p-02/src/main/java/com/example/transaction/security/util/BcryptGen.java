package com.example.transaction.security.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 临时工具：生成 BCrypt 密码哈希
 * 用完可删除
 */
public class BcryptGen {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String raw = args.length > 0 ? args[0] : "123456";
        System.out.println(encoder.encode(raw));
    }
}
