package com.stability.martrix.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 根因指向结果
 */
public class RootCauseInsight {

    private String rootCause;
    private String suspectedLibrary;
    private String evidenceType;
    private String reasoning;
    private List<String> triggers = new ArrayList<>();
    private List<String> solutions = new ArrayList<>();
    private List<String> prevention = new ArrayList<>();

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getSuspectedLibrary() {
        return suspectedLibrary;
    }

    public void setSuspectedLibrary(String suspectedLibrary) {
        this.suspectedLibrary = suspectedLibrary;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
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
