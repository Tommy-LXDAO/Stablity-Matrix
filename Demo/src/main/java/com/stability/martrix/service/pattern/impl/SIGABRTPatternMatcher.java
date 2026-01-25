package com.stability.martrix.service.pattern.impl;

import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.enums.SignalType;
import com.stability.martrix.exception.InvalidTombstoneException;
import com.stability.martrix.service.pattern.SignalPatternMatcher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pattern matcher for SIGABRT (signal 6)
 * Analyzes abort signals, usually from abort() calls
 */
@Service
public class SIGABRTPatternMatcher implements SignalPatternMatcher {

    @Override
    public PatternMatchResult match(AArch64Tombstone tombstone) {
        // Check for null or empty stack trace
        if (tombstone.getStackDumpInfo() == null ||
            tombstone.getStackDumpInfo().getStackFrames() == null ||
            tombstone.getStackDumpInfo().getStackFrames().isEmpty()) {
            throw new InvalidTombstoneException("No stack trace available for analysis");
        }

        // Validate it's a true SIGABRT: must have 'abort' symbol AND ('musl' or 'libc') in mapsInfo
        if (!isTrueAbort(tombstone)) {
            return createLowConfidenceResult("Not a valid SIGABRT signal: missing abort symbol or C library reference");
        }

        // Mode 1: Check for Double Free
        PatternMatchResult doubleFreeResult = checkDoubleFree(tombstone);
        if (doubleFreeResult != null) {
            return doubleFreeResult;
        }

        // Mode 2: Check for Assertion Failure
        PatternMatchResult assertionFailureResult = checkAssertionFailure(tombstone);
        if (assertionFailureResult != null) {
            return assertionFailureResult;
        }

        // Mode 3: Check for Direct abort() call by business logic
        PatternMatchResult directAbortResult = checkDirectAbort(tombstone);
        if (directAbortResult != null) {
            return directAbortResult;
        }
        // TODO: 拼接message给AI使用

        // No specific pattern matched
        return null;
    }

    /**
     * Validate it's a true SIGABRT signal
     * Must have 'abort' symbol AND ('musl' or 'libc') in mapsInfo
     */
    private boolean isTrueAbort(AArch64Tombstone tombstone) {
        boolean hasAbortSymbol = false;
        boolean hasCLibrary = false;

        for (AArch64Tombstone.StackDumpInfo.StackFrame frame : tombstone.getStackDumpInfo().getStackFrames()) {
            // Check for abort symbol
            if (frame.getSymbol() != null && frame.getSymbol().toLowerCase().contains("abort")) {
                hasAbortSymbol = true;
            }

            // Check for C library in mapsInfo (musl or libc)
            if (frame.getMapsInfo() != null &&
                (frame.getMapsInfo().contains("musl") || frame.getMapsInfo().contains("libc"))) {
                hasCLibrary = true;
            }

            // Early exit if both conditions are met
            if (hasAbortSymbol && hasCLibrary) {
                return true;
            }
        }

        return hasAbortSymbol && hasCLibrary;
    }

    /**
     * Check for double free patterns in stack trace
     * Double free is caught by C library (bionic/musl) in the free() function
     */
    private PatternMatchResult checkDoubleFree(AArch64Tombstone tombstone) {
        if (tombstone.getStackDumpInfo() == null ||
            tombstone.getStackDumpInfo().getStackFrames() == null ||
            tombstone.getStackDumpInfo().getStackFrames().isEmpty()) {
            return null;
        }

        // Get the first stack frame (where the crash occurred - #00)
        AArch64Tombstone.StackDumpInfo.StackFrame crashFrame =
            tombstone.getStackDumpInfo().getStackFrames().get(0);

        String mapsInfo = crashFrame.getMapsInfo();
        String symbol = crashFrame.getSymbol();

        if (mapsInfo == null || symbol == null) {
            return null;
        }

        // Check if crash occurred in C library (bionic or musl)
        boolean inCLibrary = mapsInfo.contains("bionic") || mapsInfo.contains("musl");

        // Check if the symbol contains free or free_default
        boolean isFreeFunction = symbol.contains("free") || symbol.contains("free_default");

        if (inCLibrary && isFreeFunction) {
            return PatternMatchResult.builder()
                .confidence(0.95)
                .result("检测到double free: Crash 在" + symbol + " 的 " + mapsInfo + "。这意味着内存被释放了两次. 请review你的代码确保正确使用 malloc/free等相关内存分配、释放函数。")
                .aiPrompt("请提醒用户检测到double free，这意味着在进程内内存使用不合规范，大部分情况下需要找进程分析，需要进程首先排查是否在代码中正确使用malloc/free相关的内存分配、释放函数。")
                .directConclusion(true)
                .build();
        }

        return null;
    }

    /**
     * Check for assertion failure patterns in stack trace
     */
    private PatternMatchResult checkAssertionFailure(AArch64Tombstone tombstone) {
        // Build stack trace string for pattern matching
        StringBuilder stackTraceBuilder = new StringBuilder();
        for (AArch64Tombstone.StackDumpInfo.StackFrame frame : tombstone.getStackDumpInfo().getStackFrames()) {
            if (frame.getSymbol() != null) {
                stackTraceBuilder.append(frame.getSymbol()).append("\n");
            }
        }
        String stackTrace = stackTraceBuilder.toString();
        // Assertion failure patterns
        String[] assertionPatterns = {
            "__assert",
            "__android_log_assert",
            "LOG_ALWAYS_FATAL",
            "LOG(FATAL",
            "CHECK",
            "DCHECK",
            "REQUIRE",
            "ASSERT",
            "rtc::",
            "base::CheckError",
            "blink::",
            "webkit"
        };

        String lowerTrace = stackTrace.toLowerCase();

        // High confidence assertion patterns
        if (lowerTrace.contains("__assert") ||
            lowerTrace.contains("__android_log_assert") ||
            lowerTrace.contains("log_always_fatal") ||
            lowerTrace.contains("log(fatal") ||
            lowerTrace.contains("check_eq") ||
            lowerTrace.contains("check_ne")) {
            return PatternMatchResult.builder()
                .confidence(0.95)
                .result("断言失败：断言检查失败。这表明程序运行中可能违反了某个逻辑检查点，请查看断言消息和堆栈跟踪以确定失败的原因。")
                .aiPrompt("Assertion failure detected. Assertion check failed in native code. Program terminated due to violated invariant.")
                .directConclusion(true)
                .build();
        }

        // Medium confidence: assertion-like patterns
        for (String pattern : assertionPatterns) {
            if (lowerTrace.contains(pattern.toLowerCase())) {
                return PatternMatchResult.builder()
                    .confidence(0.80)
                    .result("可能断言失败：在堆栈跟踪中检测到断言或健全性检查失败。程序由于条件检查失败而主动终止。")
                    .aiPrompt("Assertion failure detected. Assertion or sanity check found in stack trace.")
                    .directConclusion(true)
                    .build();
            }
        }

        return null;
    }

    /**
     * Check for direct abort() call by upper-layer business logic
     * Logic: Check if the first frame (#00) is abort() in bionic/musl (libc.so),
     * and the next frame (#01) is from a different mapsInfo (the caller)
     */
    private PatternMatchResult checkDirectAbort(AArch64Tombstone tombstone) {
        if (tombstone.getStackDumpInfo() == null ||
            tombstone.getStackDumpInfo().getStackFrames() == null ||
            tombstone.getStackDumpInfo().getStackFrames().isEmpty()) {
            return null;
        }

        List<AArch64Tombstone.StackDumpInfo.StackFrame> frames = tombstone.getStackDumpInfo().getStackFrames();

        // Need at least 2 frames: #00 (abort in libc) and #01 (caller)
        if (frames.size() < 2) {
            return null;
        }

        // Traverse the stack to find the real caller (skip consecutive abort frames)
        AArch64Tombstone.StackDumpInfo.StackFrame realCallerFrame = null;

        // Only check the first 2 frames
        for (int i = 0; i < 2; i++) {
            AArch64Tombstone.StackDumpInfo.StackFrame frame = frames.get(i);
            String symbol = frame.getSymbol();
            String mapsInfo = frame.getMapsInfo();
            if (symbol == null || mapsInfo == null) {
                throw new InvalidTombstoneException("Frame #" + i + " has null symbol or mapsInfo, cannot analyze abort pattern symbol="+symbol+" mapsInfo="+mapsInfo);
            }
            // If this frame is also an abort(), continue looking
            boolean inCLibrary = mapsInfo.contains("bionic") || mapsInfo.contains("musl") || mapsInfo.contains("libc.so");
            if (inCLibrary && symbol.contains("abort")) {
                if (i + 1 < frames.size()) {
                    realCallerFrame = frames.get(i + 1);

                }
                break;
            }
        }

        // If all frames are abort(), fall back to frame #01
        if (realCallerFrame == null) {
            // return null
            return null;
        }

        String callerMapsInfo = realCallerFrame.getMapsInfo();

        if (callerMapsInfo == null) {
            return null;
        }

        // Return result directly without additional judgment
        String aiPrompt = String.format("直接调用abort()，abort的接口规格就是导致进程挂掉，用户是一个小白，并不明白其中的逻辑，请详细解释并提示用户需要找调用者继续分析，而非找栈顶libc分析 - 调用者: %s",
            callerMapsInfo);
        String result = "该so直接调用了abort，需要找这个so继续分析:" + callerMapsInfo;

        return PatternMatchResult.builder()
            .confidence(1.0)
            .result(result)
            .aiPrompt(aiPrompt)
            .directConclusion(true)
            .build();
    }

    /**
     * Create a low confidence result for unknown patterns
     */
    private PatternMatchResult createLowConfidenceResult(String description) {
        return PatternMatchResult.builder()
            .confidence(0.30)
            .result(description)
            .aiPrompt("Unknown or unclassified crash pattern. Unable to determine specific cause.")
            .directConclusion(false)
            .build();
    }

    @Override
    public int getSupportedSignalNumber() {
        return SignalType.SIGABRT.getSignalNumber();
    }
}
