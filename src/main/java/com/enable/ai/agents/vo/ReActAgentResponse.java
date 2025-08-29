package com.enable.ai.agents.vo;

import com.enable.ai.util.XmlTagExtractor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString
public class ReActAgentResponse {

    public ReActAgentResponse(String response) {
        extractAndCleanFromResponse(response);
    }

    private String task;
    private String thought;
    private String action;
    private String observation;
    private String finalAnswer;

    /**
     * 从响应字符串中提取各个字段的值
     *
     * @param response 包含XML标签的响应字符串
     */
    public void extractFromResponse(String response) {
        this.task = extractTagContent(response, "task");
        this.thought = extractTagContent(response, "thought");
        this.action = extractTagContent(response, "action");
        this.observation = extractTagContent(response, "observation");
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
        return task != null && thought != null && action != null && observation != null && finalAnswer != null;
    }

    /**
     * 检查是否有最终答案
     *
     * @return true 如果finalAnswer不为null且不为空
     */
    public boolean hasFinalAnswer() {
        return finalAnswer != null && !finalAnswer.trim().isEmpty();
    }

    public boolean hasAction() {
        return action != null && !action.trim().isEmpty();
    }

    public boolean hasThought() {
        return thought != null && !thought.trim().isEmpty();
    }

    public boolean hasObservation() {
        return observation != null && !observation.trim().isEmpty();
    }

    public boolean hasTask() {
        return task != null && !task.trim().isEmpty();
    }

    /**
     * 清理提取的内容（去除多余的空白字符）
     */
    public void cleanupContent() {
        if (task != null) {
            task = task.trim();
        }
        if (thought != null) {
            thought = thought.trim();
        }
        if (action != null) {
            action = action.trim();
        }
        if (observation != null) {
            observation = observation.trim();
        }
        if (finalAnswer != null) {
            finalAnswer = finalAnswer.trim();
        }
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

        if (thought != null && !thought.isEmpty()) {
            sb.append("Thought: ").append(thought).append("\n");
        }

        if (action != null && !action.isEmpty()) {
            sb.append("Action: ").append(action).append("\n");
        }

        if (observation != null && !observation.isEmpty()) {
            sb.append("Observation: ").append(observation).append("\n");
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
                <task>告诉我埃菲尔铁塔有多高？</task>
                <thought>我需要找到埃菲尔铁塔的高度。可以使用搜索工具。</thought>
                <action>get_height("埃菲尔铁塔")</action>
                <observation>埃菲尔铁塔的高度约为330米（包含天线）。</observation>
                <thought>搜索结果显示了高度。我已经得到答案了。</thought>
                <final_answer>埃菲尔铁塔的高度约为330米。</final_answer>
                """;

        ReActAgentResponse response = new ReActAgentResponse();
        response.extractAndCleanFromResponse(responseXml);

        System.out.println("提取的内容：");
        System.out.println(response.toFormattedString());

        System.out.println("是否完整: " + response.isComplete());
        System.out.println("是否有最终答案: " + response.hasFinalAnswer());
    }
}