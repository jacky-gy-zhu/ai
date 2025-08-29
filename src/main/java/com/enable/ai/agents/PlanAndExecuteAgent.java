package com.enable.ai.agents;

import com.enable.ai.agents.vo.LeadAgentResponse;
import com.enable.ai.service.PromptRagService;
import com.enable.ai.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
            response.setExecutionLog(response.getExecutionLog() + "\nQuestion: " + nextStep + "\nAnswer: " + subAnswer);
        } while (!response.hasFinalAnswer());

        return response.getFinalAnswer();
    }

    @Override
    public String streamChat(long userId, String userPrompt, SseEmitter emitter) {
        try {
            LeadAgentResponse response = null;
            do {
                if (response == null) {
                    response = new LeadAgentResponse();
                    response.setTask(userPrompt);
                }
                log.info(">>> LeadAgentResponse before chat: {}", response);
                response = new LeadAgentResponse(leadAgent.chat(userId, response));
                log.info(">>> LeadAgentResponse after chat: {}", response);
                if (!response.hasFinalAnswer()) {
                    sseService.sendPlanEvent(emitter, response);
                    String nextStep = response.getNextStep();
                    sseService.sendNextStepEvent(emitter, response);
                    String subAnswer = reActAgent.streamChat(userId, nextStep, emitter);
                    response.setExecutionLog(StringUtils.defaultString(response.getExecutionLog()) + "\nQuestion: " + nextStep + "\nAnswer: " + subAnswer);
                }
            } while (!response.hasFinalAnswer());

            sseService.sendFinalAnswerEvent(emitter, response.getFinalAnswer());

            return response.getFinalAnswer();
        } catch (Exception e) {
            log.error("Error in stream chat", e);
            sseService.sendMessageEvent(emitter, "处理聊天时出错: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
