package com.stability.martrix.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI相关配置类
 */
@Configuration
public class AIConfig {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;
    
    @Value("${spring.ai.openai.base-url}")
    private String openAiBaseUrl;

    @Bean
    public ChatClient chatClient() {
        OpenAiApi openAiApi = new OpenAiApi(openAiBaseUrl, new SimpleApiKey(openAiApiKey));
        OpenAiChatModel openAiChatModel = new OpenAiChatModel(openAiApi, 
            OpenAiChatOptions.builder()
                .withModel("gpt-3.5-turbo")
                .build());
        return ChatClient.builder(openAiChatModel).build();
    }
}