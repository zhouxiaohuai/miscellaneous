package com.example.transaction.workflow.repository;

import com.example.transaction.workflow.entity.WfInstanceNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WfInstanceNodeRepository extends JpaRepository<WfInstanceNode, Long> {

    List<WfInstanceNode> findByInstanceId(Long instanceId);

    List<WfInstanceNode> findByInstanceIdAndStatus(Long instanceId, Integer status);

    Optional<WfInstanceNode> findByInstanceIdAndNodeId(Long instanceId, Long nodeId);
}
