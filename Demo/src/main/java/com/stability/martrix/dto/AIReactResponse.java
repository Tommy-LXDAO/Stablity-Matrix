package com.stability.martrix.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct对话响应
 */
public class AIReactResponse extends BaseResponse {

    private String sessionId;
    private String question;
    private String answer;
    private String finalThought;
    private List<ReactStep> steps = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getFinalThought() {
        return finalThought;
    }

    public void setFinalThought(String finalThought) {
        this.finalThought = finalThought;
    }

    public List<ReactStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ReactStep> steps) {
        this.steps = steps;
    }

    public void addStep(ReactStep step) {
        if (this.steps == null) {
            this.steps = new ArrayList<>();
        }
        this.steps.add(step);
    }

    public static AIReactResponse fail(String errorCode, String errorMessage) {
        AIReactResponse response = new AIReactResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setErrorMessage(errorMessage);
        return response;
    }

    /**
     * ReAct执行步骤
     */
    public static class ReactStep {
        private int step;
        private String thought;
        private String action;
        private String actionInput;
        private String observation;

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }

        public String getThought() {
            return thought;
        }

        public void setThought(String thought) {
            this.thought = thought;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getActionInput() {
            return actionInput;
        }

        public void setActionInput(String actionInput) {
            this.actionInput = actionInput;
        }

        public String getObservation() {
            return observation;
        }

        public void setObservation(String observation) {
            this.observation = observation;
        }
    }
}
