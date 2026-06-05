package com.example.transaction.jvm.execution;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 类加载器层级演示
 *
 * 类加载器层级（双亲委派模型）：
 *
 * ┌─────────────────────────────────────────┐
 * │       Bootstrap ClassLoader              │  C++ 实现
 * │       加载: java.lang.* 等核心类          │  rt.jar (JDK 8+)
 * │       路径: $JAVA_HOME/lib               │  modules (JDK 9+)
 * ├─────────────────────────────────────────┤
 * │       Extension ClassLoader              │  Java 实现
 * │       加载: javax.* 等扩展类              │  sun.misc.Launcher$ExtClassLoader
 * │       路径: $JAVA_HOME/lib/ext           │
 * ├─────────────────────────────────────────┤
 * │       Application ClassLoader            │  Java 实现
 * │       加载: 应用 classpath 下的类         │  sun.misc.Launcher$AppClassLoader
 * │       路径: -cp / classpath              │
 * ├─────────────────────────────────────────┤
 * │       Custom ClassLoader                 │  用户自定义
 * │       加载: 特殊路径/网络/加密的类         │
 * └─────────────────────────────────────────┘
 *
 * 双亲委派（Parent Delegation）：
 * 1. 收到加载请求，先委托给父加载器
 * 2. 父加载器无法加载时，才自己尝试
 * 3. 保证核心类不被篡改（如用户自定义 java.lang.String 不会被加载）
 *
 * 打破双亲委派的场景：
 * 1. SPI（Service Provider Interface）— 线程上下文类加载器
 * 2. Tomcat — 每个 Web 应用独立 ClassLoader
 * 3. OSGi — 网状委托模型
 * 4. 热部署 — 自定义 ClassLoader 重新加载修改后的类
 */
@Component
public class ClassLoaderDemo {

    /**
     * 展示类加载器层级
     *
     * 每个类都有一个 classLoader 引用，指向加载它的类加载器
     * 通过 getClassLoader() 可以追溯层级关系
     */
    public Map<String, Object> showClassLoaderHierarchy() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 当前类的类加载器
        ClassLoader appLoader = this.getClass().getClassLoader();
        ClassLoader extLoader = appLoader.getParent();
        ClassLoader bootLoader = extLoader.getParent();

        Map<String, Object> current = new LinkedHashMap<>();
        current.put("类名", this.getClass().getName());
        current.put("ClassLoader", appLoader.getClass().getName());
        result.put("当前类加载器", current);

        Map<String, Object> hierarchy = new LinkedHashMap<>();
        hierarchy.put("Application", appLoader.getClass().getName());
        hierarchy.put("Extension", extLoader.getClass().getName());
        hierarchy.put("Bootstrap", bootLoader != null ? bootLoader.getClass().getName() : "null（C++ 实现，Java 中无法直接引用）");
        result.put("层级关系", hierarchy);

        // 核心类的类加载器
        Map<String, Object> coreClasses = new LinkedHashMap<>();
        coreClasses.put("java.lang.String", String.class.getClassLoader()); // null = Bootstrap
        coreClasses.put("java.util.HashMap", java.util.HashMap.class.getClassLoader()); // null = Bootstrap
        coreClasses.put("javax.swing.JFrame", javax.swing.JFrame.class != null ? "Extension" : "N/A");
        coreClasses.put("com.example.transaction.jvm.execution.ClassLoaderDemo", appLoader.getClass().getName());
        result.put("各类的加载器", coreClasses);

        result.put("说明", """
            Bootstrap 加载的核心类 getClassLoader() 返回 null
            这不是没有加载器，而是 Bootstrap 用 C++ 实现，Java 中无法获取引用
            """);

        return result;
    }

    /**
     * 演示双亲委派的工作流程
     *
     * 以加载 java.lang.String 为例：
     * 1. AppClassLoader 收到请求 → 委托给 ExtClassLoader
     * 2. ExtClassLoader → 委托给 BootstrapClassLoader
     * 3. BootstrapClassLoader 在 rt.jar 找到 → 加载成功
     * 4. 结果逐层返回，AppClassLoader 不再自己加载
     *
     * 以加载 com.example.transaction.Xxx 为例：
     * 1. AppClassLoader 收到请求 → 委托给 ExtClassLoader
     * 2. ExtClassLoader → 委托给 BootstrapClassLoader
     * 3. BootstrapClassLoader 找不到 → 返回
     * 4. ExtClassLoader 找不到 → 返回
     * 5. AppClassLoader 在 classpath 找到 → 加载成功
     */
    public Map<String, Object> demonstrateParentDelegation() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("流程图", """
            loadClass("java.lang.String")
            │
            ├─ AppClassLoader
            │  └─ 委托给父加载器
            │     └─ ExtClassLoader
            │        └─ 委托给父加载器
            │           └─ BootstrapClassLoader
            │              ├─ 在 rt.jar 找到 → 返回 String.class
            │              └─ 找不到 → 返回 null
            │
            └─ 加载成功，不再自己查找
            """);

        result.put("核心代码", """
            // ClassLoader.loadClass() 源码简化版
            protected Class<?> loadClass(String name, boolean resolve) {
                // 1. 检查是否已加载
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try {
                        // 2. 委托给父加载器
                        if (parent != null) {
                            c = parent.loadClass(name, false);
                        } else {
                            // 3. 父加载器为 null，用 Bootstrap 加载
                            c = findBootstrapClassOrNull(name);
                        }
                    } catch (ClassNotFoundException e) {
                        // 父加载器无法加载
                    }
                    if (c == null) {
                        // 4. 自己加载
                        c = findClass(name);
                    }
                }
                return c;
            }
            """);

        result.put("优点", """
            1. 安全性：核心类不会被篡改（用户自定义的 java.lang.String 不会被加载）
            2. 唯一性：同一个类只会被加载一次（由最顶层能加载它的 ClassLoader 加载）
            """);

        return result;
    }

    /**
     * 演示自定义类加载器
     *
     * 继承 ClassLoader，重写 findClass()（推荐）
     * 不建议重写 loadClass()（会破坏双亲委派）
     */
    public Map<String, Object> demonstrateCustomClassLoader() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 获取应用类加载器
        ClassLoader appLoader = this.getClass().getClassLoader();

        result.put("自定义类加载器的作用", """
            1. 热部署：修改代码后用新 ClassLoader 重新加载
            2. 加密类：加载时解密字节码
            3. 网络加载：从远程服务器加载类
            4. 隔离：不同 ClassLoader 加载的同名类是不同的类
            """);

        result.put("实现方式", """
            // 推荐：重写 findClass()
            public class MyClassLoader extends ClassLoader {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    byte[] bytes = loadClassData(name); // 从自定义路径读取字节码
                    return defineClass(name, bytes, 0, bytes.length);
                }
            }

            // 不推荐：重写 loadClass()（破坏双亲委派）
            // Tomcat 为了 Web 应用隔离，重写了 loadClass()
            """);

        result.put("Tomcat 的类加载器", """
            Bootstrap
              └─ System
                 └─ Common (Tomcat 公共类)
                    ├─ WebApp1 (Web 应用1的类)
                    └─ WebApp2 (Web 应用2的类)

            每个 WebApp 独立 ClassLoader，实现应用隔离：
            - WebApp1 和 WebApp2 可以有不同版本的同名类
            - 先从自己加载，找不到才委托给父加载器（打破双亲委派）
            """);

        return result;
    }

    /**
     * 演示类的唯一性 — 不同 ClassLoader 加载的同名类是不同的
     *
     * 即使字节码完全相同，由不同 ClassLoader 加载的类：
     * - Class 对象不同
     * - instanceof 返回 false
     * - 互相赋值会抛 ClassCastException
     */
    public Map<String, Object> demonstrateClassIdentity() {
        Map<String, Object> result = new LinkedHashMap<>();

        ClassLoader loader1 = new ClassLoader() {};
        ClassLoader loader2 = new ClassLoader() {};

        // 用不同 ClassLoader 加载同一个类
        try {
            // 这里用 String 举例（实际不能自定义加载 java.lang.String）
            // 改用自定义类来演示
            Class<?> c1 = loader1.loadClass("com.example.transaction.jvm.memory.ReferenceTypeDemo");
            Class<?> c2 = loader2.loadClass("com.example.transaction.jvm.memory.ReferenceTypeDemo");
            Class<?> c3 = this.getClass().getClassLoader().loadClass("com.example.transaction.jvm.memory.ReferenceTypeDemo");

            result.put("loader1 加载", c1.getClassLoader().getClass().getName());
            result.put("loader2 加载", c2.getClassLoader().getClass().getName());
            result.put("AppClassLoader 加载", c3.getClassLoader().getClass().getName());
            result.put("c1 == c2", c1 == c2);
            result.put("c1 == c3", c1 == c3);
            result.put("c2 == c3", c2 == c3);
            result.put("说明", """
                不同 ClassLoader 加载的同名类：
                - Class 对象不同（c1 != c2 != c3）
                - 不能互相赋值（会抛 ClassCastException）
                - instanceof 返回 false
                这就是 Tomcat 实现 Web 应用隔离的原理
                """);
        } catch (ClassNotFoundException e) {
            result.put("错误", e.getMessage());
        }

        return result;
    }

    /**
     * 演示 SPI 打破双亲委派
     *
     * SPI（Service Provider Interface）问题：
     * - 接口由 Bootstrap ClassLoader 加载（java.util.spi.*）
     * - 实现类在 classpath（由 Application ClassLoader 加载）
     * - Bootstrap 无法反向委托给 Application → 打破双亲委派
     *
     * 解决方案：线程上下文类加载器（Thread Context ClassLoader）
     * Thread.currentThread().getContextClassLoader()
     */
    public Map<String, Object> demonstrateSPI() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("SPI 问题", """
            java.sql.Driver（接口）由 Bootstrap ClassLoader 加载
            com.mysql.cj.jdbc.Driver（实现）在 classpath
            Bootstrap 无法加载 Application 的类 → 需要打破双亲委派
            """);

        result.put("解决方案: 线程上下文类加载器", """
            // ServiceLoader 源码简化
            public static <S> ServiceLoader<S> load(Class<S> service) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                return ServiceLoader.load(service, cl);
            }

            // DriverManager 加载驱动
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            for (Driver driver : ServiceLoader.load(Driver.class, cl)) {
                // 用线程上下文 ClassLoader 加载实现类
            }
            """);

        result.put("当前线程上下文 ClassLoader", Thread.currentThread().getContextClassLoader().getClass().getName());

        result.put("打破双亲委派的场景", """
            1. SPI — 线程上下文 ClassLoader
            2. Tomcat — 重写 loadClass()，先自己加载再委托父
            3. OSGi — 网状委托模型
            4. 热部署 — 新 ClassLoader 替代旧的
            5. Groovy/JRuby — 动态语言运行时
            """);

        return result;
    }
}
