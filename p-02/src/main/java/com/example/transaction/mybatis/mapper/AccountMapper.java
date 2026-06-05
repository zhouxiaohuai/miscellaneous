package com.example.transaction.mybatis.mapper;

import com.example.transaction.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AccountMapper {

    Account findById(@Param("id") Long id);

    Account findByUserId(@Param("userId") Long userId);

    List<Account> findAll();

    int insert(Account account);

    int updateBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    int deleteById(@Param("id") Long id);
}
