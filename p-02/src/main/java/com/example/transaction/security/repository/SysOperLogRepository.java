package com.example.transaction.security.repository;

import com.example.transaction.security.entity.SysOperLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysOperLogRepository extends JpaRepository<SysOperLog, Long> {
}
