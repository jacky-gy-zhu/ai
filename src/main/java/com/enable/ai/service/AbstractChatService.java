package com.enable.ai.service;

public abstract class AbstractChatService {

    protected boolean isFinalAnswerPresent(String answer) {
        return answer.contains("<final_answer>");
    }

    protected String addXmlTagToUserPrompt(String userPrompt, String promptXmlTag) {
        return promptXmlTag != null ? ("<" + promptXmlTag + ">" + userPrompt + "</" + promptXmlTag + ">") : userPrompt;
    }

}
