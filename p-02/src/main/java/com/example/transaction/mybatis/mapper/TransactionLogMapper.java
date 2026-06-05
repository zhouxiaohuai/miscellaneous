package com.example.transaction.mybatis.mapper;

import com.example.transaction.entity.TransactionLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TransactionLogMapper {

    int insert(TransactionLog log);

    List<TransactionLog> findAll();
}
