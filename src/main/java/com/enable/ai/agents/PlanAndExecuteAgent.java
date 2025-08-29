package com.enable.ai.agents;

import com.enable.ai.agents.vo.LeadAgentResponse;
import com.enable.ai.service.PromptRagService;
import com.enable.ai.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class PlanAndExecuteAgent implements AiAgent {

    @Autowired
    private ReActAgent reActAgent;

    @Autowired
    public LeadAgent leadAgent;

    @Autowired
    private PromptRagService promptRagService;

    @Autowired
    private SseService sseService;

    @Override
    public String chat(long userId, String userPrompt) {
        LeadAgentResponse response = new LeadAgentResponse();
        response.setTask(userPrompt);
        do {
            response = new LeadAgentResponse(leadAgent.chat(userId, response));
            String nextStep = response.getNextStep();
            String subAnswer = reActAgent.chat(userId, nextStep);
            response.setExecutionLog(response.getExecutionLog() + "\nQ: " + nextStep + "\nA: " + subAnswer);
        } while (!response.hasFinalAnswer());

        return response.getFinalAnswer();
    }

    @Override
    public String streamChat(long userId, String userPrompt, SseEmitter emitter) {
        int stepCount = 1;
        try {
            LeadAgentResponse response = new LeadAgentResponse();
            response.setTask(userPrompt);
            do {
                response = new LeadAgentResponse(leadAgent.chat(userId, response));
                String nextStep = response.getNextStep();
                sseService.sendEvent(emitter, "reasoning_step", Map.of(
                        "type", "thought",
                        "content", "执行计划: \n" + response.getPlan(),
                        "step_number", stepCount++
                ));
                String subAnswer = reActAgent.streamChat(userId, nextStep, emitter);
                response.setExecutionLog(response.getExecutionLog() + "\nQ: " + nextStep + "\nA: " + subAnswer);
            } while (!response.hasFinalAnswer());

            sseService.sendEvent(emitter, "done", Map.of("final_content", response.getFinalAnswer()));

            return response.getFinalAnswer();
        } catch (Exception e) {
            log.error("Error in stream chat", e);
            try {
                sseService.sendEvent(emitter, "error", Map.of("message", "处理聊天时出错: " + e.getMessage()));
                emitter.completeWithError(e);
            } catch (IOException ioException) {
                log.error("Error sending error event", ioException);
            }
            return "Error: " + e.getMessage();
        } finally {
            // 清理ThreadLocal
            sseService.getSentReasoningSteps().remove();
        }
    }
}
