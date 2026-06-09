package com.example.transaction.workflow.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * SpEL 条件评估器
 *
 * 将流程变量注入 SpEL 上下文，评估连线条件表达式。
 *
 * 表达式示例：
 * - "#amount > 1000"                    — 金额大于 1000
 * - "#vip == true"                      — 是 VIP
 * - "#amount > 5000 && #region == 'overseas'"  — 复合条件
 * - null / 空                            — 无条件（始终通过）
 */
@Slf4j
@Service
public class ConditionEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    /**
     * 评估条件表达式
     *
     * @param expressionText SpEL 表达式，null 或空表示无条件（始终返回 true）
     * @param variables      流程变量，如 {"amount": 1500, "vip": true}
     * @return 条件是否满足
     */
    public boolean evaluate(String expressionText, Map<String, Object> variables) {
        // null / 空 = 无条件，始终通过
        if (expressionText == null || expressionText.isBlank()) {
            log.debug("[流程引擎] 条件表达式为空，视为无条件通过");
            return true;
        }

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();

            // 将流程变量注入上下文，以 # 前缀访问
            if (variables != null) {
                variables.forEach((key, value) -> {
                    context.setVariable(key, value);
                    log.debug("[流程引擎] 注入变量: #{} = {}", key, value);
                });
            }

            Expression expression = parser.parseExpression(expressionText);
            Boolean result = expression.getValue(context, Boolean.class);

            log.info("[流程引擎] 条件评估: {} → {}（变量: {}）", expressionText, result, variables);
            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            log.error("[流程引擎] 条件评估失败: {}，变量: {}", expressionText, variables, e);
            throw new WorkflowException("条件表达式评估失败: " + expressionText, e);
        }
    }
}
