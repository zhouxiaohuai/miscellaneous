package com.example.transaction.jvm.controller;

import com.example.transaction.jvm.execution.*;
import com.example.transaction.jvm.gc.*;
import com.example.transaction.jvm.memory.*;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JVM 演示接口
 *
 * 涵盖三大模块：
 * 1. 内存模型 — /api/jvm/memory/*
 * 2. 执行原理 — /api/jvm/execution/*
 * 3. 垃圾回收 — /api/jvm/gc/*
 */
@RestController
@RequestMapping("/api/jvm")
public class JvmDemoController {

    // === 内存模块 ===
    private final MemoryStructureDemo memoryStructureDemo;
    private final StackHeapDemo stackHeapDemo;
    private final StringPoolDemo stringPoolDemo;
    private final DirectMemoryDemo directMemoryDemo;
    private final ReferenceTypeDemo referenceTypeDemo;

    // === 执行原理模块 ===
    private final BytecodeDemo bytecodeDemo;
    private final ClassLoadDemo classLoadDemo;
    private final ClassLoaderDemo classLoaderDemo;
    private final JitCompileDemo jitCompileDemo;

    // === GC 模块 ===
    private final GcAlgorithmDemo gcAlgorithmDemo;
    private final GcCollectorDemo gcCollectorDemo;
    private final MemoryLeakDemo memoryLeakDemo;
    private final GcTuningDemo gcTuningDemo;
    private final FinalizeVsCleanerDemo finalizeVsCleanerDemo;

    public JvmDemoController(
            MemoryStructureDemo memoryStructureDemo,
            StackHeapDemo stackHeapDemo,
            StringPoolDemo stringPoolDemo,
            DirectMemoryDemo directMemoryDemo,
            ReferenceTypeDemo referenceTypeDemo,
            BytecodeDemo bytecodeDemo,
            ClassLoadDemo classLoadDemo,
            ClassLoaderDemo classLoaderDemo,
            JitCompileDemo jitCompileDemo,
            GcAlgorithmDemo gcAlgorithmDemo,
            GcCollectorDemo gcCollectorDemo,
            MemoryLeakDemo memoryLeakDemo,
            GcTuningDemo gcTuningDemo,
            FinalizeVsCleanerDemo finalizeVsCleanerDemo) {
        this.memoryStructureDemo = memoryStructureDemo;
        this.stackHeapDemo = stackHeapDemo;
        this.stringPoolDemo = stringPoolDemo;
        this.directMemoryDemo = directMemoryDemo;
        this.referenceTypeDemo = referenceTypeDemo;
        this.bytecodeDemo = bytecodeDemo;
        this.classLoadDemo = classLoadDemo;
        this.classLoaderDemo = classLoaderDemo;
        this.jitCompileDemo = jitCompileDemo;
        this.gcAlgorithmDemo = gcAlgorithmDemo;
        this.gcCollectorDemo = gcCollectorDemo;
        this.memoryLeakDemo = memoryLeakDemo;
        this.gcTuningDemo = gcTuningDemo;
        this.finalizeVsCleanerDemo = finalizeVsCleanerDemo;
    }

    // ==================== 内存模型 ====================

    @GetMapping("/memory/structure")
    public Map<String, Object> memoryStructure() {
        return memoryStructureDemo.showMemoryStructure();
    }

    @GetMapping("/memory/heap-generations")
    public Map<String, Object> heapGenerations() {
        return memoryStructureDemo.showHeapGenerations();
    }

    @GetMapping("/memory/stack-heap")
    public Map<String, Object> stackVsHeap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("栈分配", stackHeapDemo.demonstrateStackAllocation());
        result.put("堆分配", stackHeapDemo.demonstrateHeapAllocation());
        result.put("逃逸分析", stackHeapDemo.demonstrateEscapeAnalysis());
        return result;
    }

    @GetMapping("/memory/stack-overflow")
    public Map<String, Object> stackOverflow() {
        return stackHeapDemo.demonstrateStackOverflow();
    }

    @GetMapping("/memory/string-pool")
    public Map<String, Object> stringPool() {
        return stringPoolDemo.demonstrateAllCases();
    }

    @GetMapping("/memory/string-pool/intern-perf")
    public Map<String, Object> internPerformance(@RequestParam(defaultValue = "10000") int count) {
        return stringPoolDemo.demonstrateInternPerformance(count);
    }

    @GetMapping("/memory/string-pool/jdk-diff")
    public Map<String, Object> stringPoolJdkDiff() {
        return stringPoolDemo.showJdkVersionDifference();
    }

    @GetMapping("/memory/direct")
    public Map<String, Object> directMemory(
            @RequestParam(defaultValue = "1024") int bufferSize,
            @RequestParam(defaultValue = "10000") int iterations) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("性能对比", directMemoryDemo.compareHeapVsDirect(bufferSize, iterations));
        result.put("零拷贝", directMemoryDemo.demonstrateZeroCopy());
        return result;
    }

    @GetMapping("/memory/reference")
    public Map<String, Object> referenceType(@RequestParam(defaultValue = "strong") String type) {
        return switch (type) {
            case "strong" -> referenceTypeDemo.demonstrateStrongReference();
            case "soft" -> referenceTypeDemo.demonstrateSoftReference();
            case "weak" -> referenceTypeDemo.demonstrateWeakReference();
            case "phantom" -> referenceTypeDemo.demonstratePhantomReference();
            case "weakhashmap" -> referenceTypeDemo.demonstrateWeakHashMap();
            default -> {
                Map<String, Object> all = new LinkedHashMap<>();
                all.put("强引用", referenceTypeDemo.demonstrateStrongReference());
                all.put("软引用", referenceTypeDemo.demonstrateSoftReference());
                all.put("弱引用", referenceTypeDemo.demonstrateWeakReference());
                all.put("虚引用", referenceTypeDemo.demonstratePhantomReference());
                all.put("WeakHashMap", referenceTypeDemo.demonstrateWeakHashMap());
                yield all;
            }
        };
    }

    // ==================== 执行原理 ====================

    @GetMapping("/execution/bytecode")
    public Map<String, Object> bytecode() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("字节码指令", bytecodeDemo.demonstrateBytecodeInstructions());
        result.put("方法分派", bytecodeDemo.demonstrateDispatch());
        result.put("Lambda 实现", bytecodeDemo.demonstrateLambdaBytecode());
        return result;
    }

    @GetMapping("/execution/classload")
    public Map<String, Object> classLoad() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("准备vs初始化", classLoadDemo.demonstratePrepareVsInit());
        result.put("被动引用", classLoadDemo.demonstratePassiveReference());
        result.put("类卸载", classLoadDemo.demonstrateClassUnload());
        return result;
    }

    @GetMapping("/execution/classload/thread-safety")
    public Map<String, Object> classLoadThreadSafety() throws InterruptedException {
        return classLoadDemo.demonstrateThreadSafety();
    }

    @GetMapping("/execution/classloader")
    public Map<String, Object> classLoader() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("层级关系", classLoaderDemo.showClassLoaderHierarchy());
        result.put("双亲委派", classLoaderDemo.demonstrateParentDelegation());
        result.put("自定义类加载器", classLoaderDemo.demonstrateCustomClassLoader());
        result.put("类唯一性", classLoaderDemo.demonstrateClassIdentity());
        result.put("SPI 打破双亲委派", classLoaderDemo.demonstrateSPI());
        return result;
    }

    @GetMapping("/execution/jit")
    public Map<String, Object> jit(
            @RequestParam(defaultValue = "1000000") int iterations) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("解释vs JIT", jitCompileDemo.demonstrateInterpretVsJit(iterations));
        result.put("方法内联", jitCompileDemo.demonstrateMethodInlining(iterations));
        result.put("逃逸分析", jitCompileDemo.demonstrateEscapeAnalysis(iterations));
        result.put("分层编译", jitCompileDemo.demonstrateTieredCompilation());
        return result;
    }

    // ==================== 垃圾回收 ====================

    @GetMapping("/gc/algorithm")
    public Map<String, Object> gcAlgorithm() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("算法对比", gcAlgorithmDemo.demonstrateAlgorithms());
        result.put("对象分配", gcAlgorithmDemo.demonstrateObjectAllocation());
        result.put("触发条件", gcAlgorithmDemo.demonstrateGcTriggers());
        result.put("当前 GC 信息", gcAlgorithmDemo.showCurrentGcInfo());
        return result;
    }

    @GetMapping("/gc/collector")
    public Map<String, Object> gcCollector() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("收集器对比", gcCollectorDemo.compareCollectors());
        result.put("选择建议", gcCollectorDemo.recommendCollector());
        result.put("G1 Region", gcCollectorDemo.demonstrateG1Regions());
        return result;
    }

    @GetMapping("/gc/leak")
    public Map<String, Object> gcLeak(@RequestParam(defaultValue = "static") String type) {
        return switch (type) {
            case "static" -> memoryLeakDemo.demonstrateStaticCollectionLeak(10000);
            case "resource" -> memoryLeakDemo.demonstrateResourceLeak();
            case "threadlocal" -> memoryLeakDemo.demonstrateThreadLocalLeak();
            case "inner" -> memoryLeakDemo.demonstrateInnerClassLeak();
            case "cache" -> memoryLeakDemo.demonstrateCacheLeak();
            case "diagnosis" -> memoryLeakDemo.showLeakDiagnosis();
            default -> {
                Map<String, Object> all = new LinkedHashMap<>();
                all.put("静态集合", memoryLeakDemo.demonstrateStaticCollectionLeak(1000));
                all.put("未关闭资源", memoryLeakDemo.demonstrateResourceLeak());
                all.put("ThreadLocal", memoryLeakDemo.demonstrateThreadLocalLeak());
                all.put("内部类", memoryLeakDemo.demonstrateInnerClassLeak());
                all.put("缓存", memoryLeakDemo.demonstrateCacheLeak());
                all.put("排查方法", memoryLeakDemo.showLeakDiagnosis());
                yield all;
            }
        };
    }

    @GetMapping("/gc/tuning")
    public Map<String, Object> gcTuning() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("参数速查", gcTuningDemo.showGcParameters());
        result.put("日志分析", gcTuningDemo.showGcLogAnalysis());
        result.put("工具", gcTuningDemo.showGcTools());
        result.put("调优案例", gcTuningDemo.showTuningCases());
        result.put("当前 JVM", gcTuningDemo.showCurrentJvmInfo());
        return result;
    }

    @GetMapping("/gc/finalize")
    public Map<String, Object> finalizeVsCleaner() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("finalize 问题", finalizeVsCleanerDemo.demonstrateFinalizeProblems());
        result.put("Cleaner 演示", finalizeVsCleanerDemo.demonstrateCleaner());
        result.put("PhantomReference", finalizeVsCleanerDemo.demonstratePhantomReferenceTracking());
        result.put("最佳实践", finalizeVsCleanerDemo.showBestPractices());
        return result;
    }

    // ==================== 总览 ====================

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("模块", "JVM 内存模型、代码运行原理、垃圾回收");

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("GET /api/jvm/memory/structure", "JVM 内存区域结构");
        memory.put("GET /api/jvm/memory/heap-generations", "堆内存分代");
        memory.put("GET /api/jvm/memory/stack-heap", "栈 vs 堆 + 逃逸分析");
        memory.put("GET /api/jvm/memory/stack-overflow", "StackOverflowError");
        memory.put("GET /api/jvm/memory/string-pool", "字符串常量池（6 种场景）");
        memory.put("GET /api/jvm/memory/string-pool/intern-perf?count=10000", "intern 性能");
        memory.put("GET /api/jvm/memory/string-pool/jdk-diff", "JDK 版本差异");
        memory.put("GET /api/jvm/memory/direct?bufferSize=1024", "直接内存");
        memory.put("GET /api/jvm/memory/reference?type=strong", "四种引用（strong/soft/weak/phantom）");
        result.put("一、内存模型", memory);

        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("GET /api/jvm/execution/bytecode", "字节码结构 + 方法分派 + Lambda");
        execution.put("GET /api/jvm/execution/classload", "类加载过程 + 被动引用 + 类卸载");
        execution.put("GET /api/jvm/execution/classload/thread-safety", "类加载线程安全");
        execution.put("GET /api/jvm/execution/classloader", "类加载器 + 双亲委派 + SPI");
        execution.put("GET /api/jvm/execution/jit?iterations=1000000", "JIT 编译 + 内联 + 逃逸分析");
        result.put("二、执行原理", execution);

        Map<String, Object> gc = new LinkedHashMap<>();
        gc.put("GET /api/jvm/gc/algorithm", "GC 算法 + 对象分配 + 触发条件");
        gc.put("GET /api/jvm/gc/collector", "七种收集器 + 选择建议 + G1 Region");
        gc.put("GET /api/jvm/gc/leak?type=static", "内存泄漏（static/resource/threadlocal/inner/cache）");
        gc.put("GET /api/jvm/gc/tuning", "GC 调优参数 + 日志 + 工具 + 案例");
        gc.put("GET /api/jvm/gc/finalize", "finalize vs Cleaner vs PhantomReference");
        result.put("三、垃圾回收", gc);

        return result;
    }
}
