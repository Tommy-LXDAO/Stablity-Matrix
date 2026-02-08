package com.stability.martrix.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 使用AI进行简单查询的服务类
 */
@Service
public class AITroubleAnalysisService {

    private final ChatClient chatClient;

    @Autowired
    public AITroubleAnalysisService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String simpleQuery(String question) {
        String systemPrompt = """
                你是一个技术专家，你的名字是小明
                """;
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPrompt);
        Prompt prompt = new Prompt(
                systemPromptTemplate.createMessage(),
                new UserMessage(question)
        );
        ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();
        if (chatResponse == null || chatResponse.getResults().isEmpty()) {
            return "获取结果失败";
        }
        return chatResponse.getResults().getFirst().getOutput().getText();
    }
}
