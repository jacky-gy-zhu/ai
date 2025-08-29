package com.enable.ai.agent;

import com.enable.ai.agent.vo.LeadAgentResponse;
import com.enable.ai.service.ChatService;
import com.enable.ai.service.SseService;
import com.enable.ai.util.PromptConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LeadAgent {

    @Autowired
    private ChatService chatService;

    @Autowired
    private SseService sseService;

    public String chat(long userId, LeadAgentResponse response) {
        return chatService.chat(userId, PromptConstants.SYSTEM_PROMPT_LEAD_MODE, buildUserPrompt(response).toString());
    }

    private static StringBuilder buildUserPrompt(LeadAgentResponse response) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("<task>").append(response.getTask()).append("</task>\n");
        if (response.hasExecuteLog()) {
            userPrompt.append("<execution_log>").append(response.getExecutionLog()).append("</execution_log>\n");
        }
        if (response.hasPlan()) {
            userPrompt.append("<plan>").append(response.getPlan()).append("</plan>\n");
        }
        if (response.hasNextStep()) {
            userPrompt.append("<next_step>").append(response.getNextStep()).append("</next_step>\n");
        }
        if (response.hasFinalAnswer()) {
            userPrompt.append("<final_answer>").append(response.getFinalAnswer()).append("</final_answer>\n");
        }
        return userPrompt;
    }
}
