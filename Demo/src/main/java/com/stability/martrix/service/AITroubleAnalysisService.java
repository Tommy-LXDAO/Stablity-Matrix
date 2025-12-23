package com.stability.martrix.service;

import com.stability.martrix.entity.AArch64Tombstone;
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
                1. 导致崩溃的可能根本原因
                2. 涉及的组件或库
                3. 可能的修复建议
                
                请用中文回答，保持专业但易懂的语言。
                """;

        // 构建用户消息内容
        String userMessageContent = buildTombstoneDescription(tombstone);

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
     * 构建tombstone描述信息
     *
     * @param tombstone AArch64Tombstone对象
     * @return 格式化的tombstone描述
     */
    private String buildTombstoneDescription(AArch64Tombstone tombstone) {
        StringBuilder sb = new StringBuilder();

        sb.append("进程信息:\n");
        sb.append("- 进程名: ").append(tombstone.getProcessName()).append("\n");
        sb.append("- PID: ").append(tombstone.getPid()).append("\n");
        sb.append("- TID: ").append(tombstone.getFirstTid()).append("\n\n");

        if (tombstone.getSignalInfo() != null) {
            sb.append("信号信息:\n");
            sb.append("- 信号编号: ").append(tombstone.getSignalInfo().getSigNumber()).append("\n");
            sb.append("- 信号名称: ").append(tombstone.getSignalInfo().getSigInformation()).append("\n");
            sb.append("- 错误类型: ").append(tombstone.getSignalInfo().getTroubleInformation()).append("\n");
            sb.append("- 故障地址: 0x").append(Long.toHexString(tombstone.getSignalInfo().getFaultAddress() != null ?
                    tombstone.getSignalInfo().getFaultAddress() : 0)).append("\n\n");
        }

        if (tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null) {
            sb.append("堆栈信息:\n");
            for (AArch64Tombstone.StackDumpInfo.StackFrame frame : tombstone.getStackDumpInfo().getStackFrames()) {
                if (frame.getIndex() < 10) { // 只显示前10个堆栈帧
                    sb.append("#").append(frame.getIndex()).append(" ");
                    sb.append("地址: 0x").append(Long.toHexString(frame.getAddress() != null ? frame.getAddress() : 0)).append(" ");
                    sb.append("库: ").append(frame.getMapsInfo() != null ? frame.getMapsInfo() : "未知").append(" ");
                    if (frame.getSymbol() != null) {
                        sb.append("符号: ").append(frame.getSymbol());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        if (tombstone.getRegisterDumpInfo() != null) {
            sb.append("关键寄存器信息:\n");
            sb.append("- X0: 0x").append(Long.toHexString(tombstone.getRegisterDumpInfo().getX0())).append("\n");
            sb.append("- X1: 0x").append(Long.toHexString(tombstone.getRegisterDumpInfo().getX1())).append("\n");
            sb.append("- X2: 0x").append(Long.toHexString(tombstone.getRegisterDumpInfo().getX2())).append("\n");
            sb.append("- X3: 0x").append(Long.toHexString(tombstone.getRegisterDumpInfo().getX3())).append("\n");
            sb.append("- PC: 0x").append(Long.toHexString(tombstone.getRegisterDumpInfo().getPc())).append("\n");
            sb.append("- SP: 0x").append(Long.toHexString(tombstone.getRegisterDumpInfo().getSp())).append("\n");
            sb.append("- LR: 0x").append(Long.toHexString(tombstone.getRegisterDumpInfo().getX30())).append("\n\n");
        }

        if (tombstone.getSpecialRegisterInfo() != null) {
            sb.append("特殊寄存器信息:\n");
            if (tombstone.getSpecialRegisterInfo().getLr() != null) {
                sb.append("- LR: 0x").append(Long.toHexString(tombstone.getSpecialRegisterInfo().getLr())).append("\n");
            }
            if (tombstone.getSpecialRegisterInfo().getSp() != null) {
                sb.append("- SP: 0x").append(Long.toHexString(tombstone.getSpecialRegisterInfo().getSp())).append("\n");
            }
            if (tombstone.getSpecialRegisterInfo().getPc() != null) {
                sb.append("- PC: 0x").append(Long.toHexString(tombstone.getSpecialRegisterInfo().getPc())).append("\n");
            }
            if (tombstone.getSpecialRegisterInfo().getPst() != null) {
                sb.append("- PST: 0x").append(Long.toHexString(tombstone.getSpecialRegisterInfo().getPst())).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}