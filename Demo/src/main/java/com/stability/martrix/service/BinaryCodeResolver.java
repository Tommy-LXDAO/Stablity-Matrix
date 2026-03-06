package com.stability.martrix.service;

import com.stability.martrix.dto.CodeLocation;
import com.stability.martrix.entity.AArch64Tombstone;

/**
 * 二进制代码解析服务接口
 * 负责将二进制地址转换为源代码行号，并读取代码片段
 */
public interface BinaryCodeResolver {

    /**
     * 从tombstone中解析栈顶地址为代码位置
     *
     * @param tombstone 崩溃的tombstone数据
     * @return 栈顶代码位置，如果没有则返回null
     */
    CodeLocation resolveTopStackFrame(AArch64Tombstone tombstone);

    /**
     * 根据单个地址解析代码位置
     *
     * @param address 二进制地址（十六进制字符串，如 "0x7f8a9b2c4d"）
     * @param libraryName 库名称（如 "libnative-lib.so"）
     * @return 代码位置
     */
    CodeLocation resolveAddress(String address, String libraryName);

    /**
     * 读取指定文件指定行号的代码片段
     *
     * @param sourceFile 源文件路径
     * @param lineNumber 行号
     * @param contextLines 上下文行数（前后各几行）
     * @return 包含上下文的代码片段
     */
    String readCodeSnippet(String sourceFile, int lineNumber, int contextLines);
}
