package com.enable.ai.agents.vo;

import com.enable.ai.util.XmlTagExtractor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LeadAgentResponse {

    public LeadAgentResponse(String response) {
        extractAndCleanFromResponse(response);
    }

    private String task;
    private String executionLog;
    private String plan;
    private String nextStep;
    private String finalAnswer;

    /**
     * 从响应字符串中提取各个字段的值
     *
     * @param response 包含XML标签的响应字符串
     */
    public void extractFromResponse(String response) {
        this.task = extractTagContent(response, "task");
        this.executionLog = extractTagContent(response, "execution_log");
        this.plan = extractTagContent(response, "plan");
        this.nextStep = extractTagContent(response, "next_step");
        this.finalAnswer = extractTagContent(response, "final_answer");
    }

    /**
     * 提取指定标签的内容
     *
     * @param response 响应字符串
     * @param tagName  标签名称
     * @return 标签内容，如果找不到返回null
     */
    private String extractTagContent(String response, String tagName) {
        return XmlTagExtractor.extractXmlTagContent(response, tagName);
    }

    /**
     * 检查是否所有必需字段都已提取
     *
     * @return true 如果所有字段都不为null
     */
    public boolean isComplete() {
        return task != null && executionLog != null &&
                plan != null && nextStep != null && finalAnswer != null;
    }

    /**
     * 检查是否有最终答案
     *
     * @return true 如果finalAnswer不为null且不为空
     */
    public boolean hasFinalAnswer() {
        return finalAnswer != null && !finalAnswer.trim().isEmpty();
    }

    public boolean hasExecuteLog() {
        return executionLog != null && !executionLog.trim().isEmpty();
    }

    public boolean hasPlan() {
        return plan != null && !plan.trim().isEmpty();
    }

    public boolean hasNextStep() {
        return nextStep != null && !nextStep.trim().isEmpty();
    }

    /**
     * 清理提取的内容（去除多余的空白字符）
     */
    public void cleanupContent() {
        if (task != null) task = task.trim();
        if (executionLog != null) executionLog = executionLog.trim();
        if (plan != null) plan = plan.trim();
        if (nextStep != null) nextStep = nextStep.trim();
        if (finalAnswer != null) finalAnswer = finalAnswer.trim();
    }

    /**
     * 从响应中提取并清理内容
     *
     * @param response 包含XML标签的响应字符串
     */
    public void extractAndCleanFromResponse(String response) {
        extractFromResponse(response);
        cleanupContent();
    }

    /**
     * 生成格式化的响应字符串
     *
     * @return 包含所有字段的格式化字符串
     */
    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Lead Agent Response ===\n");

        if (task != null && !task.isEmpty()) {
            sb.append("Task: ").append(task).append("\n");
        }

        if (plan != null && !plan.isEmpty()) {
            sb.append("\nPlan:\n").append(plan).append("\n");
        }

        if (executionLog != null && !executionLog.isEmpty()) {
            sb.append("\nExecution Log:\n").append(executionLog).append("\n");
        }

        if (nextStep != null && !nextStep.isEmpty()) {
            sb.append("\nNext Step:\n").append(nextStep).append("\n");
        }

        if (finalAnswer != null && !finalAnswer.isEmpty()) {
            sb.append("\nFinal Answer:\n").append(finalAnswer).append("\n");
        }

        return sb.toString();
    }

    /**
     * 示例使用方法
     */
    public static void main(String[] args) {
        String responseXml = """
                <task>分析用户需求并生成报告</task>
                <execution_log>
                    1. 接收到用户请求
                    2. 开始分析数据
                    3. 生成初步结果
                </execution_log>
                <plan>
                    - 数据收集
                    - 数据分析
                    - 报告生成
                </plan>
                <next_step>继续收集更多数据</next_step>
                <final_answer>分析完成，报告已生成</final_answer>
                """;

        LeadAgentResponse response = new LeadAgentResponse();
        response.extractAndCleanFromResponse(responseXml);

        System.out.println("提取的内容：");
        System.out.println(response.toFormattedString());

        System.out.println("是否完整: " + response.isComplete());
        System.out.println("是否有最终答案: " + response.hasFinalAnswer());
    }
}