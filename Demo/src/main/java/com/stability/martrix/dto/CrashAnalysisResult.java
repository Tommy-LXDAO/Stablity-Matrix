package com.stability.martrix.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * AI崩溃分析结果DTO
 */
public class CrashAnalysisResult {

    /**
     * 崩溃的直接原因
     */
    @JsonProperty("rootCause")
    private String rootCause;

    /**
     * 可能的触发场景
     */
    @JsonProperty("triggers")
    private List<String> triggers;

    /**
     * 解决方案
     */
    @JsonProperty("solutions")
    private List<String> solutions;

    /**
     * 预防措施
     */
    @JsonProperty("prevention")
    private List<String> prevention;

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public List<String> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<String> triggers) {
        this.triggers = triggers;
    }

    public List<String> getSolutions() {
        return solutions;
    }

    public void setSolutions(List<String> solutions) {
        this.solutions = solutions;
    }

    public List<String> getPrevention() {
        return prevention;
    }

    public void setPrevention(List<String> prevention) {
        this.prevention = prevention;
    }
}
