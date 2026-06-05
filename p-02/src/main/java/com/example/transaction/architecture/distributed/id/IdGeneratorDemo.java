package com.example.transaction.architecture.distributed.id;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分布式 ID 模块 — 公开门面
 */
@Slf4j
@Component
public class IdGeneratorDemo {

    /**
     * 生成分布式 ID
     *
     * @param type  类型：snowflake / uuid / increment
     * @param count 生成数量
     */
    public Map<String, Object> generate(String type, int count) {
        log.info("===== 分布式 ID 演示 =====");

        IdGenerator generator = switch (type) {
            case "uuid" -> new UuidIdGenerator();
            case "increment" -> new IncrementIdGenerator(1000);
            default -> new SnowflakeIdGenerator(1, 1);
        };

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(generator.nextStringId());
        }

        return Map.of(
                "type", generator.name(),
                "count", count,
                "ids", ids
        );
    }
}
