package com.example.transaction.jpa.repository;

import com.example.transaction.entity.IsolationTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IsolationTestRepository extends JpaRepository<IsolationTest, Long> {

    Optional<IsolationTest> findByName(String name);

    @Modifying
    @Query("UPDATE IsolationTest t SET t.value = :value WHERE t.name = :name")
    int updateValueByName(@Param("name") String name, @Param("value") Integer value);
}
