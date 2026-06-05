# study-log.md（学习笔记：只追加、不覆盖）

说明：本文件记录 Java 学习笔记与进度。每次学习/提问/复盘都在末尾追加新日期小节。

---

## 2026-05-12 阶段重启：从「集合框架」开始补内功

### 当前状态回顾
- 之前完成了并发编程 7 课（JMM → 线程池 → CompletableFuture）
- 但集合、JVM、泛型/反射、IO 等核心模块还是空的
- 并发学得不错，但「地基」需要补——集合是 Java 中使用频率最高的 API

### 本阶段目标（集合框架）
- **能选对**：知道什么时候用 ArrayList / LinkedList / HashMap / TreeMap / LinkedHashMap 等
- **能看源码**：核心类的关键方法能读懂（add、get、put、resize 等）
- **能避坑**：fail-fast、并发修改异常、hashCode/equals 约定、内存占用等
- **能排障**：遇到 OOM、性能瓶颈时能从集合使用角度排查

### 今日学习主题
见下方最新日期条目。
