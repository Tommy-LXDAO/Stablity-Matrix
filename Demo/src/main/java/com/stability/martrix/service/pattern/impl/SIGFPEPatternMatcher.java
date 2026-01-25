package com.stability.martrix.service.pattern.impl;

import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.enums.SignalType;
import com.stability.martrix.service.pattern.SignalPatternMatcher;
import org.springframework.stereotype.Service;

/**
 * Pattern matcher for SIGFPE (signal 8)
 * Analyzes floating point exceptions: divide by zero, overflow, etc.
 */
@Service
public class SIGFPEPatternMatcher implements SignalPatternMatcher {

    @Override
    public PatternMatchResult match(AArch64Tombstone tombstone) {
        // TODO: Implement SIGFPE pattern matching logic
        // Analyze:
        // - Divide by zero operations
        // - Floating point overflow
        // - Integer overflow
        // - Check signal code for specific FPE type
        return null;
    }

    @Override
    public int getSupportedSignalNumber() {
        return SignalType.SIGFPE.getSignalNumber();
    }
}
