package com.stability.martrix.service;

import com.alibaba.fastjson.JSON;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.register.AArch64RegisterDumpInfo;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 使用AI分析故障原因的服务类
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
        return chatClient.prompt(prompt).call().content();
    }

    /**
     * 分析AArch64Tombstone故障原因
     *
     * @param tombstone AArch64Tombstone对象
     * @return 故障原因分析结果
     */
    public String analyzeTrouble(AArch64Tombstone tombstone) {
        // 构建系统提示词
        String systemPrompt = """
                你是一个Android系统专家，专门分析Native层的crash问题。
                你需要根据提供的tombstone信息分析故障原因，并给出简明扼要的解释。
                分析应包括：
                1. 导致崩溃的根本原因
                2. 问题类型：内存访问错误、还是被特殊终止等等其他因素
                3. 可能的修复建议，并指向应该由哪一层so进行排查
                
                请用中文回答，保持专业但易懂的语言。
                """;

        // 将tombstone对象转换为JSON格式
        String tombstoneJson = convertTombstoneToJson(tombstone);
//        return tombstoneJson;
        // 构建用户消息内容，包含JSON格式的tombstone数据
        String userMessageContent = """
                请分析以下tombstone信息：
                
                %s
                """.formatted(tombstoneJson);

        // 创建提示词
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPrompt);
        Prompt prompt = new Prompt(
                systemPromptTemplate.createMessage(),
                new UserMessage(userMessageContent)
        );

        // 调用AI模型并返回结果
        return chatClient.prompt(prompt).call().content();
    }

    /**
     * 将AArch64Tombstone对象转换为JSON格式字符串
     */
    private String convertTombstoneToJson(AArch64Tombstone tombstone) {
        return JSON.toJSONString(tombstone);
    }

    /**
     * 获取指定索引的寄存器值
     */
    private long getRegisterValue(AArch64RegisterDumpInfo regInfo, int index) {
        switch (index) {
            case 0: return regInfo.getX0();
            case 1: return regInfo.getX1();
            case 2: return regInfo.getX2();
            case 3: return regInfo.getX3();
            case 4: return regInfo.getX4();
            case 5: return regInfo.getX5();
            case 6: return regInfo.getX6();
            case 7: return regInfo.getX7();
            case 8: return regInfo.getX8();
            case 9: return regInfo.getX9();
            case 10: return regInfo.getX10();
            case 11: return regInfo.getX11();
            case 12: return regInfo.getX12();
            case 13: return regInfo.getX13();
            case 14: return regInfo.getX14();
            case 15: return regInfo.getX15();
            case 16: return regInfo.getX16();
            case 17: return regInfo.getX17();
            case 18: return regInfo.getX18();
            case 19: return regInfo.getX19();
            case 20: return regInfo.getX20();
            case 21: return regInfo.getX21();
            case 22: return regInfo.getX22();
            case 23: return regInfo.getX23();
            case 24: return regInfo.getX24();
            case 25: return regInfo.getX25();
            case 26: return regInfo.getX26();
            case 27: return regInfo.getX27();
            case 28: return regInfo.getX28();
            case 29: return regInfo.getX29();
            case 30: return regInfo.getX30();
            default: return 0;
        }
    }
}