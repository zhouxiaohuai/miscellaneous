package com.example.transaction.workflow.repository;

import com.example.transaction.workflow.entity.WfTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WfTransitionRepository extends JpaRepository<WfTransition, Long> {

    List<WfTransition> findByProcessId(Long processId);

    List<WfTransition> findBySourceNodeIdOrderBySortOrder(Long sourceNodeId);
}
