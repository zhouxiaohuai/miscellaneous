package com.example.transaction.workflow.repository;

import com.example.transaction.workflow.entity.WfInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WfInstanceRepository extends JpaRepository<WfInstance, Long> {

    List<WfInstance> findByBusinessTypeAndBusinessId(String businessType, String businessId);

    List<WfInstance> findByProcessIdAndStatus(Long processId, Integer status);

    Optional<WfInstance> findByBusinessTypeAndBusinessIdAndStatus(
            String businessType, String businessId, Integer status);
}
