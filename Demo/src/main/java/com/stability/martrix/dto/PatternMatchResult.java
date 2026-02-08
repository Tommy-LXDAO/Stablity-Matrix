package com.stability.martrix.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pattern matching result class
 * Contains confidence level, analysis result for users, and internal prompt for AI (not shown to users)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatternMatchResult {
    /**
     * Confidence level: 0.0 to 1.0
     */
    private double confidence;

    /**
     * Analysis result shown to users
     * This is the actual conclusion from pattern matching
     */
    private String result;

    /**
     * Internal prompt sent to AI (not shown to users)
     * Contains the detailed context for AI analysis
     * This is kept separate from user-facing result
     */
    private String aiPrompt;

    /**
     * Whether to draw a direct conclusion from this pattern match
     */
    private boolean directConclusion;
}
