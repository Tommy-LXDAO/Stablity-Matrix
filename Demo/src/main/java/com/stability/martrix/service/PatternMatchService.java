package com.stability.martrix.service;

import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.service.pattern.PatternMatcherFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for pattern matching analysis of crash data
 * Provides high-level interface for signal pattern matching
 */
@Service
public class PatternMatchService {

    private final PatternMatcherFactory patternMatcherFactory;

    @Autowired
    public PatternMatchService(PatternMatcherFactory patternMatcherFactory) {
        this.patternMatcherFactory = patternMatcherFactory;
    }

    /**
     * Perform pattern matching analysis on the tombstone data
     *
     * @param tombstone the tombstone data to analyze
     * @return PatternMatchResult containing confidence, description, and conclusion flag
     */
    public PatternMatchResult analyzePattern(AArch64Tombstone tombstone) {
        return patternMatcherFactory.match(tombstone);
    }

    /**
     * Check if pattern matching is supported for this tombstone
     *
     * @param tombstone the tombstone data to check
     * @return true if a pattern matcher is available for this signal type
     */
    public boolean isPatternMatchingSupported(AArch64Tombstone tombstone) {
        if (tombstone.getSignalInfo() == null) {
            return false;
        }

        int signalNumber = tombstone.getSignalInfo().getSigNumber();
        return patternMatcherFactory.getMatcher(signalNumber).isPresent();
    }
}
