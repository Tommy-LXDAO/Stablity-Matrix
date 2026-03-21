package com.stability.martrix.dto;

/**
 * Session级工具执行结果
 *
 * @param <T> 工具产出的结构化数据
 */
public class SessionToolResult<T> {

    private String toolName;
    private boolean success;
    private String observation;
    private T data;

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public static <T> SessionToolResult<T> success(String toolName, String observation, T data) {
        SessionToolResult<T> result = new SessionToolResult<>();
        result.setToolName(toolName);
        result.setSuccess(true);
        result.setObservation(observation);
        result.setData(data);
        return result;
    }

    public static <T> SessionToolResult<T> fail(String toolName, String observation, T data) {
        SessionToolResult<T> result = new SessionToolResult<>();
        result.setToolName(toolName);
        result.setSuccess(false);
        result.setObservation(observation);
        result.setData(data);
        return result;
    }
}
