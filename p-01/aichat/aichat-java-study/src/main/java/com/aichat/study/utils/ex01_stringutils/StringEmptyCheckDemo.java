package com.aichat.study.utils.ex01_stringutils;

/**
 * 练习：字符串判空的各种方式对比
 *
 * 常见判空姿势：
 * - str == null
 * - str.isEmpty()                    // JDK 1.6+，空串 "" 返回 true，null 抛 NPE
 * - str.equals("")                   // 空串 "" 返回 true，null 抛 NPE
 * - str.length() == 0                // 空串 "" 返回 true，null 抛 NPE
 * - str.isBlank()                    // JDK 11+，空白字符（空格、tab 等）也视为空
 * - str.trim().isEmpty()             // 先 trim 再判空（兼容 JDK 8）
 * - str == null || str.isEmpty()     // 安全判空（兼容 JDK 6）
 * - str == null || str.isBlank()     // 安全判空白（JDK 11+）
 * - Objects.isNull(str)              // JDK 7+，工具类方式
 * - org.apache.commons.lang3.StringUtils.isEmpty(str)     // 第三方库
 * - org.apache.commons.lang3.StringUtils.isBlank(str)     // 第三方库
 */
public final class StringEmptyCheckDemo {

  public static void main(String[] args) {
    System.out.println("Demo: 字符串判空方式对比");
    System.out.println("=".repeat(60));

    String[] samples = {
      null,
      "",
      "   ",
      "\t\n",
      "hello",
      " a "
    };

    for (String s : samples) {
      System.out.println("\n输入: " + repr(s));
      System.out.println("  isNull             = " + (s == null));
      System.out.println("  isEmpty()          = " + safeIsEmpty(s));
      System.out.println("  isBlank()(JDK11+)  = " + safeIsBlank(s));
      System.out.println("  length() == 0      = " + safeLengthZero(s));
      System.out.println("  trim().isEmpty()   = " + safeTrimIsEmpty(s));
    }

    System.out.println("\n" + "=".repeat(60));
    System.out.println("推荐用法：");
    System.out.println("  JDK 8 及以下 → (str == null || str.trim().isEmpty())");
    System.out.println("  JDK 11+      → (str == null || str.isBlank())");
    System.out.println("  第三方库     → StringUtils.isBlank(str)");
  }

  /** 安全调用 isEmpty()，避免 null 时抛 NPE */
  private static boolean safeIsEmpty(String s) {
    try {
      return s.isEmpty();
    } catch (NullPointerException e) {
      return true;
    }
  }

  /** 安全调用 isBlank()，避免 null 时抛 NPE */
  private static boolean safeIsBlank(String s) {
    try {
      return s.isBlank();
    } catch (NullPointerException e) {
      return true;
    }
  }

  /** 安全调用 length() == 0，避免 null 时抛 NPE */
  private static boolean safeLengthZero(String s) {
    try {
      return s.length() == 0;
    } catch (NullPointerException e) {
      return true;
    }
  }

  /** 安全调用 trim().isEmpty()，避免 null 时抛 NPE */
  private static boolean safeTrimIsEmpty(String s) {
    try {
      return s.trim().isEmpty();
    } catch (NullPointerException e) {
      return true;
    }
  }

  /** 对 null 安全地打印表示 */
  private static String repr(String s) {
    return s == null ? "null" : "\"" + escape(s) + "\"";
  }

  private static String escape(String s) {
    return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }
}
