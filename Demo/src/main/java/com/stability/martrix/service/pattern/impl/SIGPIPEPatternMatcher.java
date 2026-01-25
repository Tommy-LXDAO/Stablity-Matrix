package com.stability.martrix.service.pattern.impl;

import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.enums.SignalType;
import com.stability.martrix.service.pattern.SignalPatternMatcher;
import org.springframework.stereotype.Service;

/**
 * Pattern matcher for SIGPIPE (signal 13)
 * Analyzes broken pipe errors: write to pipe/socket with no reader
 */
@Service
public class SIGPIPEPatternMatcher implements SignalPatternMatcher {

    @Override
    public PatternMatchResult match(AArch64Tombstone tombstone) {
        // TODO: Implement SIGPIPE pattern matching logic
        // Analyze:
        // - Socket/pipe operations in stack trace
        // - Write operations to closed descriptors
        // - Check file descriptor info for broken pipes
        return null;
    }

    @Override
    public int getSupportedSignalNumber() {
        return SignalType.SIGPIPE.getSignalNumber();
    }
}
