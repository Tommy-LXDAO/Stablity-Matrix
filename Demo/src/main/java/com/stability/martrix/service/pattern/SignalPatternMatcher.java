package com.stability.martrix.service.pattern;

import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;

/**
 * Interface for signal pattern matchers
 * Each implementation analyzes a specific signal type and returns a pattern match result
 */
public interface SignalPatternMatcher {

    /**
     * Match and analyze the tombstone data for this signal pattern
     *
     * @param tombstone the tombstone data to analyze
     * @return PatternMatchResult containing confidence, description, and conclusion flag
     */
    PatternMatchResult match(AArch64Tombstone tombstone);

    /**
     * Get the signal number this matcher supports
     *
     * @return signal number (e.g., 6 for SIGABRT, 11 for SIGSEGV)
     */
    int getSupportedSignalNumber();
}
