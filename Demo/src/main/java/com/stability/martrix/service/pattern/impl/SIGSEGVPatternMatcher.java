package com.stability.martrix.service.pattern.impl;

import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.enums.SignalType;
import com.stability.martrix.exception.InvalidTombstoneException;
import com.stability.martrix.service.pattern.SignalPatternMatcher;
import org.springframework.stereotype.Service;

/**
 * Pattern matcher for SIGSEGV (signal 11)
 * Analyzes segmentation faults: invalid memory access
 */
@Service
public class SIGSEGVPatternMatcher implements SignalPatternMatcher {

    @Override
    public PatternMatchResult match(AArch64Tombstone tombstone) {
        // Check for null signal info
        if (tombstone.getSignalInfo() == null) {
            throw new InvalidTombstoneException("No signal information available for SIGSEGV analysis");
        }

        // Validate it's a true SIGSEGV
        if (tombstone.getSignalInfo().getSigNumber() != SignalType.SIGSEGV.getSignalNumber()) {
            throw new InvalidTombstoneException("Invalid signal number for SIGSEGV analysis");
        }

        // Mode 1: Check for Null Pointer Dereference
        PatternMatchResult nullPointerResult = checkNullPointerDereference(tombstone);
        if (nullPointerResult != null) {
            return nullPointerResult;
        }

        // Mode 2: Check for Stack Overflow
        PatternMatchResult stackOverflowResult = checkStackOverflow(tombstone);
        if (stackOverflowResult != null) {
            return stackOverflowResult;
        }

        // Mode 3: Check for Heap Corruption/Use After Free
        PatternMatchResult heapCorruptionResult = checkHeapCorruption(tombstone);
        if (heapCorruptionResult != null) {
            return heapCorruptionResult;
        }

        // Mode 4: Check for Dangling Pointer
        PatternMatchResult danglingPointerResult = checkDanglingPointer(tombstone);
        if (danglingPointerResult != null) {
            return danglingPointerResult;
        }

        // Mode 5: Check for Invalid Memory Access (wild pointer, buffer overflow)
        PatternMatchResult invalidAccessResult = checkInvalidMemoryAccess(tombstone);
        if (invalidAccessResult != null) {
            return invalidAccessResult;
        }

        // No specific pattern matched
        return null;
    }

    /**
     * Check for null pointer dereference
     * Fault address is 0x0 or very small (< 0x1000)
     */
    private PatternMatchResult checkNullPointerDereference(AArch64Tombstone tombstone) {
        if (tombstone.getSignalInfo() == null || tombstone.getSignalInfo().getFaultAddress() == null) {
            return null;
        }

        long faultAddress = tombstone.getSignalInfo().getFaultAddress();

        // Null pointer: fault address is 0x0 or very small (< 0x1000)
        if (faultAddress == 0x0L || faultAddress < 0x1000L) {
            String callerInfo = getCrashCallerInfo(tombstone);

            return PatternMatchResult.builder()
                .confidence(0.98)
                .result("检测到空指针解引用: 故障地址为 0x" + Long.toHexString(faultAddress) +
                    "，这表示程序尝试访问空指针或接近0的无效地址。" + callerInfo +
                    " 请检查代码中是否有未初始化的指针或未判空就直接使用的指针。")
                .aiPrompt("检测到空指针解引用，故障地址为 0x" + Long.toHexString(faultAddress) +
                    "。这是典型的空指针访问问题。请提醒用户：1) 检查指针是否在使用前进行了判空；2) " +
                    "检查是否有未初始化的指针变量；3) 检查函数返回值是否可能为空指针；4) " +
                    "建议使用AddressSanitizer或Valgrind工具进行检测。" + callerInfo)
                .directConclusion(true)
                .build();
        }

        return null;
    }

    /**
     * Check for stack overflow
     * Fault address is near stack pointer (sp) or in stack guard page
     */
    private PatternMatchResult checkStackOverflow(AArch64Tombstone tombstone) {
        // Check if fault address is available
        if (tombstone.getSignalInfo() == null || tombstone.getSignalInfo().getFaultAddress() == null) {
            return null;
        }

        Long faultAddress = tombstone.getSignalInfo().getFaultAddress();

        // Get stack pointer from special register info or register dump info
        Long stackPointer = getStackPointer(tombstone);

        if (stackPointer == null) {
            return null;
        }

        // Calculate distance between fault address and stack pointer
        long distance = Math.abs(faultAddress - stackPointer);

        // Stack overflow: fault address is within 4KB of stack pointer
        // or fault address is in a typical stack guard page (very large negative offset)
        boolean nearStackPointer = distance < 0x1000L; // Within 4KB

        // Also check if fault address is in a very low memory region (possible stack corruption)
        boolean inLowMemory = faultAddress < 0x1000L && faultAddress > 0x0L;

        if (nearStackPointer || inLowMemory) {
            String stackTraceInfo = getStackTraceSummary(tombstone);
            String recursionInfo = checkRecursionPattern(tombstone);

            return PatternMatchResult.builder()
                .confidence(nearStackPointer ? 0.95 : 0.85)
                .result("检测到栈溢出: 故障地址 0x" + Long.toHexString(faultAddress) +
                    (nearStackPointer ? " 接近栈指针(SP=0x" + Long.toHexString(stackPointer) + ")" :
                        " 位于低内存区域") +
                    "。这通常是由于无限递归或过大的局部变量导致的。" +
                    recursionInfo + stackTraceInfo)
                .aiPrompt("检测到栈溢出，故障地址 0x" + Long.toHexString(faultAddress) +
                    (nearStackPointer ? "，接近栈指针" : "，位于低内存区域") +
                    "。请提醒用户：1) 检查是否存在无限递归；2) 检查是否有大数组作为局部变量；" +
                    "3) 考虑增加线程栈大小；4) 检查递归终止条件是否正确。" +
                    recursionInfo + stackTraceInfo)
                .directConclusion(true)
                .build();
        }

        return null;
    }

    /**
     * Check for heap corruption or use-after-free
     * Fault address is in heap region (non-null but invalid access)
     */
    private PatternMatchResult checkHeapCorruption(AArch64Tombstone tombstone) {
        if (tombstone.getSignalInfo() == null || tombstone.getSignalInfo().getFaultAddress() == null) {
            return null;
        }

        Long faultAddress = tombstone.getSignalInfo().getFaultAddress();

        // Heap addresses are typically > 0x1000 and < 0x800000000000 (userspace)
        // Exclude very small addresses (null pointer) and very large addresses (kernel)
        boolean isHeapRegion = faultAddress >= 0x1000L && faultAddress < 0x800000000000L;

        if (!isHeapRegion) {
            return null;
        }

        // Check stack trace for heap-related functions
        boolean hasHeapPattern = false;
        StringBuilder heapPatterns = new StringBuilder();

        if (tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null) {
            for (AArch64Tombstone.StackDumpInfo.StackFrame frame : tombstone.getStackDumpInfo().getStackFrames()) {
                if (frame.getSymbol() != null) {
                    String symbol = frame.getSymbol().toLowerCase();

                    // Check for free/delete/malloc/new patterns
                    if (symbol.contains("free") || symbol.contains("delete") ||
                        symbol.contains("malloc") || symbol.contains("new")) {
                        hasHeapPattern = true;
                        heapPatterns.append(" 发现堆相关函数: ").append(frame.getSymbol()).append(";");
                    }
                }
            }
        }

        if (hasHeapPattern) {
            return PatternMatchResult.builder()
                .confidence(0.90)
                .result("检测到堆内存问题（可能是释放后使用use-after-free或双重释放double-free）: " +
                    "故障地址 0x" + Long.toHexString(faultAddress) +
                    " 位于堆区域。" + heapPatterns +
                    " 请检查内存管理代码，确保free/delete后的指针不再被使用。")
                .aiPrompt("检测到堆内存问题，故障地址 0x" + Long.toHexString(faultAddress) +
                    "。堆栈中发现内存管理函数调用：" + heapPatterns +
                    "请提醒用户：1) 检查是否有use-after-free问题（free/delete后继续使用指针）；" +
                    "2) 检查是否有double-free问题（同一内存释放两次）；" +
                    "3) 检查是否有heap buffer overflow（越界访问堆内存）；" +
                    "4) 建议使用AddressSanitizer或Valgrind进行检测。")
                .directConclusion(true)
                .build();
        }

        // If fault address is in heap region but no heap pattern in stack trace,
        // it could still be heap corruption (wild pointer, corrupted pointer)
        return PatternMatchResult.builder()
            .confidence(0.70)
            .result("可能是堆内存损坏: 故障地址 0x" + Long.toHexString(faultAddress) +
                " 位于堆区域，但堆栈中未发现明显的内存管理函数。" +
                " 这可能是野指针或已损坏的指针。请检查指针的生命周期和初始化。")
            .aiPrompt("可能是堆内存损坏，故障地址 0x" + Long.toHexString(faultAddress) +
                "。建议：1) 检查指针是否指向已释放的内存；2) 检查是否有数组越界导致堆破坏；" +
                "3) 检查指针初始化；4) 使用AddressSanitizer检测。")
            .directConclusion(false)
            .build();
    }

    /**
     * Check for dangling pointer (pointer to freed memory)
     * Similar to heap corruption but focuses on accessing freed memory
     */
    private PatternMatchResult checkDanglingPointer(AArch64Tombstone tombstone) {
        if (tombstone.getSignalInfo() == null || tombstone.getSignalInfo().getFaultAddress() == null) {
            return null;
        }

        Long faultAddress = tombstone.getSignalInfo().getFaultAddress();

        // Dangling pointer typically points to valid-looking address but memory is unmapped
        boolean isValidLookingAddress = faultAddress >= 0x1000L && faultAddress < 0x100000000L;

        if (!isValidLookingAddress) {
            return null;
        }

        // Check if there's a free/delete in the stack trace
        boolean hasFreeInStack = false;
        String freeSymbol = null;

        if (tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null) {
            for (int i = 0; i < Math.min(5, tombstone.getStackDumpInfo().getStackFrames().size()); i++) {
                AArch64Tombstone.StackDumpInfo.StackFrame frame = tombstone.getStackDumpInfo().getStackFrames().get(i);
                if (frame.getSymbol() != null) {
                    String symbol = frame.getSymbol().toLowerCase();
                    if (symbol.contains("free") || symbol.contains("delete")) {
                        hasFreeInStack = true;
                        freeSymbol = frame.getSymbol();
                        break;
                    }
                }
            }
        }

        if (hasFreeInStack) {
            return PatternMatchResult.builder()
                .confidence(0.92)
                .result("检测到悬空指针（Dangling Pointer/Use-After-Free）: " +
                    "故障地址 0x" + Long.toHexString(faultAddress) +
                    "，堆栈中发现 " + freeSymbol + " 调用。" +
                    " 这表明程序可能在释放内存后继续使用该指针。")
                .aiPrompt("检测到悬空指针问题，故障地址 0x" + Long.toHexString(faultAddress) +
                    "。堆栈中发现" + freeSymbol + "调用。请提醒用户：1) 这是典型的use-after-free问题；" +
                    "2) free/delete后应立即将指针置为nullptr；3) 检查对象生命周期管理；" +
                    "4) 考虑使用智能指针(auto_ptr/shared_ptr/unique_ptr)自动管理内存。")
                .directConclusion(true)
                .build();
        }

        return null;
    }

    /**
     * Check for invalid memory access (wild pointer, buffer overflow, etc.)
     */
    private PatternMatchResult checkInvalidMemoryAccess(AArch64Tombstone tombstone) {
        if (tombstone.getSignalInfo() == null || tombstone.getSignalInfo().getFaultAddress() == null) {
            return null;
        }

        Long faultAddress = tombstone.getSignalInfo().getFaultAddress();
        String troubleInfo = tombstone.getSignalInfo().getTroubleInformation();

        // Check if trouble info contains access error details
        boolean isAccessError = troubleInfo != null &&
            (troubleInfo.contains("SEGV_ACCERR") || troubleInfo.contains("access error"));

        // Check for obviously invalid addresses (very large values, often wild pointers)
        boolean isWildPointer = faultAddress > 0x100000000000L; // Very large address

        if (isAccessError || isWildPointer) {
            String callerInfo = getCrashCallerInfo(tombstone);
            String reason = isAccessError ? "访问权限错误（可能是写入只读内存）" :
                "野指针（指针值异常：0x" + Long.toHexString(faultAddress) + "）";

            return PatternMatchResult.builder()
                .confidence(isWildPointer ? 0.95 : 0.85)
                .result("检测到非法内存访问: " + reason +
                    "，故障地址 0x" + Long.toHexString(faultAddress) +
                    "。" + callerInfo +
                    " 请检查指针运算、数组边界和内存访问权限。")
                .aiPrompt("检测到非法内存访问，" + reason +
                    "，故障地址 0x" + Long.toHexString(faultAddress) +
                    "。请提醒用户：1) 检查数组访问是否越界；2) 检查指针运算是否正确；" +
                    "3) 检查是否尝试写入只读内存；4) 检查野指针（未初始化或已失效的指针）；" +
                    callerInfo)
                .directConclusion(true)
                .build();
        }

        return null;
    }

    /**
     * Get stack pointer from special register info or register dump info
     */
    private Long getStackPointer(AArch64Tombstone tombstone) {
        // Try special register info first
        if (tombstone.getSpecialRegisterInfo() != null && tombstone.getSpecialRegisterInfo().getSp() != null) {
            return tombstone.getSpecialRegisterInfo().getSp();
        }

        // Try register dump info
        if (tombstone.getRegisterDumpInfo() != null && tombstone.getRegisterDumpInfo().getSp() != 0) {
            return tombstone.getRegisterDumpInfo().getSp();
        }

        return null;
    }

    /**
     * Get crash caller information from stack trace
     */
    private String getCrashCallerInfo(AArch64Tombstone tombstone) {
        if (tombstone.getStackDumpInfo() == null ||
            tombstone.getStackDumpInfo().getStackFrames() == null ||
            tombstone.getStackDumpInfo().getStackFrames().isEmpty()) {
            return "";
        }

        // Get the first non-library frame (the actual caller code)
        for (int i = 1; i < tombstone.getStackDumpInfo().getStackFrames().size(); i++) {
            AArch64Tombstone.StackDumpInfo.StackFrame frame = tombstone.getStackDumpInfo().getStackFrames().get(i);
            if (frame.getMapsInfo() != null &&
                !frame.getMapsInfo().contains("libc") &&
                !frame.getMapsInfo().contains("musl") &&
                !frame.getMapsInfo().contains("bionic") &&
                frame.getSymbol() != null) {
                return " 调用者: " + frame.getSymbol() + " (" + frame.getMapsInfo() + ")";
            }
        }

        return "";
    }

    /**
     * Get stack trace summary for reporting
     */
    private String getStackTraceSummary(AArch64Tombstone tombstone) {
        if (tombstone.getStackDumpInfo() == null ||
            tombstone.getStackDumpInfo().getStackFrames() == null) {
            return "";
        }

        var frames = tombstone.getStackDumpInfo().getStackFrames();
        int count = Math.min(5, frames.size());

        if (count == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(" 堆栈前").append(count).append("帧: ");
        for (int i = 0; i < count; i++) {
            var frame = frames.get(i);
            if (frame.getSymbol() != null) {
                sb.append(frame.getSymbol());
                if (i < count - 1) {
                    sb.append(" -> ");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Check for recursion pattern in stack trace
     */
    private String checkRecursionPattern(AArch64Tombstone tombstone) {
        if (tombstone.getStackDumpInfo() == null ||
            tombstone.getStackDumpInfo().getStackFrames() == null) {
            return "";
        }

        var frames = tombstone.getStackDumpInfo().getStackFrames();
        if (frames.size() < 3) {
            return "";
        }

        // Check for repeated function names (potential infinite recursion)
        java.util.Set<String> seenSymbols = new java.util.HashSet<>();
        for (AArch64Tombstone.StackDumpInfo.StackFrame frame : frames) {
            if (frame.getSymbol() != null && !frame.getSymbol().isEmpty()) {
                if (seenSymbols.contains(frame.getSymbol())) {
                    return " 发现递归模式: " + frame.getSymbol();
                }
                seenSymbols.add(frame.getSymbol());
            }
        }

        return "";
    }

    /**
     * Create a low confidence result for unknown patterns
     */
    private PatternMatchResult createLowConfidenceResult(String description) {
        return PatternMatchResult.builder()
            .confidence(0.30)
            .result(description)
            .aiPrompt("Unknown or unclassified SIGSEGV pattern. Unable to determine specific cause.")
            .directConclusion(false)
            .build();
    }

    @Override
    public int getSupportedSignalNumber() {
        return SignalType.SIGSEGV.getSignalNumber();
    }
}
