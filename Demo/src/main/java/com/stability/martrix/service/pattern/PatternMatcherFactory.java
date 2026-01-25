package com.stability.martrix.service.pattern;

import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Factory class for signal pattern matchers
 * Routes to the appropriate matcher based on signal number
 */
@Component
public class PatternMatcherFactory {

    private final List<SignalPatternMatcher> patternMatchers;

    @Autowired
    public PatternMatcherFactory(List<SignalPatternMatcher> patternMatchers) {
        this.patternMatchers = patternMatchers;
    }

    /**
     * Get the appropriate pattern matcher for the given signal number
     *
     * @param signalNumber the signal number
     * @return Optional SignalPatternMatcher, empty if no matcher found
     */
    public Optional<SignalPatternMatcher> getMatcher(int signalNumber) {
        return patternMatchers.stream()
                .filter(matcher -> matcher.getSupportedSignalNumber() == signalNumber)
                .findFirst();
    }

    /**
     * Match the tombstone using the appropriate pattern matcher
     *
     * @param tombstone the tombstone data to analyze
     * @return PatternMatchResult if a matcher is found, null otherwise
     */
    public PatternMatchResult match(AArch64Tombstone tombstone) {
        if (tombstone.getSignalInfo() == null) {
            return null;
        }

        int signalNumber = tombstone.getSignalInfo().getSigNumber();
        Optional<SignalPatternMatcher> matcher = getMatcher(signalNumber);

        return matcher.map(m -> m.match(tombstone)).orElse(null);
    }

    /**
     * Get all registered pattern matchers
     *
     * @return list of all pattern matchers
     */
    public List<SignalPatternMatcher> getAllMatchers() {
        return patternMatchers;
    }
}
