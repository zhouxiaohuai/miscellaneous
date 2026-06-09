package com.example.transaction.workflow.repository;

import com.example.transaction.workflow.entity.WfNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WfNodeRepository extends JpaRepository<WfNode, Long> {

    List<WfNode> findByProcessId(Long processId);

    Optional<WfNode> findByProcessIdAndNodeKey(Long processId, String nodeKey);

    Optional<WfNode> findByProcessIdAndNodeType(Long processId, String nodeType);
}
