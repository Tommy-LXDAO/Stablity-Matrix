package com.stability.martrix.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 编程指导结果
 */
public class ProgrammingAdvice {

    private String topic;
    private String guidance;
    private List<String> steps = new ArrayList<>();
    private String exampleSnippet;

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getGuidance() {
        return guidance;
    }

    public void setGuidance(String guidance) {
        this.guidance = guidance;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }

    public String getExampleSnippet() {
        return exampleSnippet;
    }

    public void setExampleSnippet(String exampleSnippet) {
        this.exampleSnippet = exampleSnippet;
    }
}
