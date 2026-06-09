package com.example.transaction.workflow.repository;

import com.example.transaction.workflow.entity.WfProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WfProcessRepository extends JpaRepository<WfProcess, Long> {

    Optional<WfProcess> findByProcessKey(String processKey);

    List<WfProcess> findByBusinessType(String businessType);

    List<WfProcess> findByStatus(Integer status);
}
