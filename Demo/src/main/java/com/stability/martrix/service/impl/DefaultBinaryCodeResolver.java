package com.stability.martrix.service.impl;

import com.stability.martrix.dto.CodeLocation;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.service.BinaryCodeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 二进制代码解析服务默认实现（空实现）
 * TODO: 需要集成LLVM addr2line工具进行实际的地址解析
 */
@Service
public class DefaultBinaryCodeResolver implements BinaryCodeResolver {

    private static final Logger logger = LoggerFactory.getLogger(DefaultBinaryCodeResolver.class);

    @Override
    public CodeLocation resolveTopStackFrame(AArch64Tombstone tombstone) {
        // 示例: addr2line -e library.so -f -C 0x7f8a9b2c4d
        logger.info("二进制代码解析（空实现）");
        return null;
    }

    @Override
    public CodeLocation resolveAddress(String address, String libraryName) {
        return null;
    }

    @Override
    public String readCodeSnippet(String sourceFile, int lineNumber, int contextLines) {
        return "";
    }
}
