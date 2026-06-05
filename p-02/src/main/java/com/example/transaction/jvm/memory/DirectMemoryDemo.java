package com.example.transaction.jvm.memory;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 直接内存（Direct Memory）演示
 *
 * 直接内存不属于 JVM 运行时数据区，但也被频繁使用。
 *
 * 内存布局对比：
 * ┌─────────────────────┬─────────────────────┐
 * │      JVM 堆内存      │     直接内存（堆外）    │
 * ├─────────────────────┼─────────────────────┤
 * │ ByteBuffer.allocate │ ByteBuffer.allocate  │
 * │     Direct          │                      │
 * │ 受 -Xmx 限制        │ 受 -XX:MaxDirect     │
 * │ GC 自动回收          │   MemorySize 限制     │
 * │ 需要拷贝到堆         │ GC 回收（Cleaner）    │
 * │                     │ NIO 直接操作，零拷贝   │
 * └─────────────────────┴─────────────────────┘
 *
 * 使用场景：
 * 1. NIO 文件/网络 IO（减少用户态↔内核态拷贝）
 * 2. 大块数据缓存（避免堆 GC 压力）
 * 3. Netty、Mmap 等框架底层
 *
 * OOM 风险：
 * -XX:MaxDirectMemorySize=256m（默认等于 -Xmx）
 * 超出抛出 OutOfMemoryError: Direct buffer memory
 */
@Component
public class DirectMemoryDemo {

    /**
     * 对比堆内存 vs 直接内存的分配和读写性能
     *
     * 堆内存：分配快，读写需要拷贝
     * 直接内存：分配慢（需要系统调用），读写快（零拷贝）
     */
    public Map<String, Object> compareHeapVsDirect(int bufferSize, int iterations) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 堆内存分配
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            fillBuffer(buffer);
            buffer.flip();
            readBuffer(buffer);
        }
        long heapTime = System.nanoTime() - start;

        // 直接内存分配
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            fillBuffer(buffer);
            buffer.flip();
            readBuffer(buffer);
        }
        long directTime = System.nanoTime() - start;

        result.put("缓冲区大小", bufferSize + " bytes");
        result.put("迭代次数", iterations);
        result.put("堆内存耗时", heapTime / 1_000_000 + " ms");
        result.put("直接内存耗时", directTime / 1_000_000 + " ms");
        result.put("性能对比", directTime < heapTime ? "直接内存更快" : "堆内存更快");
        result.put("说明", """
            小缓冲区：堆内存更快（分配开销小）
            大缓冲区：直接内存更快（零拷贝优势明显）
            NIO 场景：直接内存避免了堆→内核的额外拷贝
            """);

        return result;
    }

    /**
     * 演示直接内存的零拷贝优势
     *
     * 传统 IO 流程（4 次拷贝）：
     * 磁盘 → 内核缓冲区 → 用户缓冲区 → Socket缓冲区 → 网卡
     *
     * DirectBuffer + MappedByteBuffer（零拷贝）：
     * 磁盘 → 内核缓冲区 → 网卡（sendfile 系统调用）
     */
    public Map<String, Object> demonstrateZeroCopy() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("传统 IO", """
            read() 系统调用：
            1. 磁盘 → 内核缓冲区（DMA 拷贝）
            2. 内核缓冲区 → 用户缓冲区（CPU 拷贝）  ← 可省略
            write() 系统调用：
            3. 用户缓冲区 → Socket 缓冲区（CPU 拷贝） ← 可省略
            4. Socket 缓冲区 → 网卡（DMA 拷贝）
            共 4 次拷贝，2 次 CPU 拷贝
            """);

        result.put("DirectBuffer", """
            使用 DirectByteBuffer：
            1. 分配堆外内存
            2. NIO 直接操作堆外内存
            3. 减少一次 用户态↔内核态 拷贝
            适合：大文件读写、网络传输
            """);

        result.put("MappedByteBuffer", """
            内存映射文件（mmap）：
            1. 文件直接映射到进程虚拟地址空间
            2. 读写文件 = 读写内存，无需 read/write 系统调用
            3. 由操作系统负责刷盘
            适合：大文件随机读写
            """);

        // 演示 MappedByteBuffer
        try {
            java.io.File tempFile = java.io.File.createTempFile("mmap_demo", ".tmp");
            tempFile.deleteOnExit();

            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(tempFile, "rw")) {
                // 写入数据
                java.nio.MappedByteBuffer mbb = raf.getChannel()
                    .map(java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, 1024);
                mbb.put("Hello MappedByteBuffer!".getBytes());

                // 读取数据
                mbb.flip();
                byte[] bytes = new byte[mbb.remaining()];
                mbb.get(bytes);
                result.put("MappedByteBuffer 读取", new String(bytes));
            }
        } catch (Exception e) {
            result.put("MappedByteBuffer 错误", e.getMessage());
        }

        return result;
    }

    /**
     * 直接内存的内存泄漏风险
     *
     * DirectByteBuffer 通过 Cleaner（PhantomReference）回收
     * 如果 GC 不及时，可能导致 OOM: Direct buffer memory
     *
     * 安全做法：
     * 1. 使用 try-with-resources（如果实现了 AutoCloseable）
     * 2. 手动调用 ((DirectBuffer) buffer).cleaner().clean()
     * 3. 设置 -XX:MaxDirectMemorySize 限制上限
     */
    public Map<String, Object> demonstrateDirectMemoryLeak(int count) {
        Map<String, Object> result = new LinkedHashMap<>();

        long beforeUsed = getDirectMemoryUsed();
        ByteBuffer[] buffers = new ByteBuffer[count];

        for (int i = 0; i < count; i++) {
            buffers[i] = ByteBuffer.allocateDirect(1024 * 1024); // 每个 1MB
        }

        long afterUsed = getDirectMemoryUsed();

        result.put("分配数量", count + " 个 1MB 缓冲区");
        result.put("分配前直接内存", formatBytes(beforeUsed));
        result.put("分配后直接内存", formatBytes(afterUsed));
        result.put("增长", formatBytes(afterUsed - beforeUsed));
        result.put("回收方式", """
            DirectByteBuffer 不在堆中，GC 通过 Cleaner（虚引用）回收
            回收时机不确定，可能导致：
            1. 堆还有很多空间 → GC 不触发 → 直接内存持续增长
            2. 最终 OOM: Direct buffer memory
            解决：手动释放 或 限制 -XX:MaxDirectMemorySize
            """);

        // 释放引用，允许 GC 回收
        for (int i = 0; i < count; i++) {
            buffers[i] = null;
        }

        return result;
    }

    private void fillBuffer(ByteBuffer buffer) {
        for (int i = 0; i < Math.min(buffer.capacity(), 1024); i++) {
            buffer.put((byte) (i % 128));
        }
    }

    private void readBuffer(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            buffer.get();
        }
    }

    private long getDirectMemoryUsed() {
        for (var pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().contains("Direct") || pool.getName().contains("direct")) {
                return pool.getUsage().getUsed();
            }
        }
        return 0;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
