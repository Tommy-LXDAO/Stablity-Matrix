package com.stability.martrix.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应父类
 * 维护成功、失败状态、错误码和错误描述信息
 */
@Data
public class BaseResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误信息描述
     */
    private String errorMessage;

    /**
     * 默认构造函数，默认为成功
     */
    public BaseResponse() {
        this.success = true;
        this.errorCode = null;
        this.errorMessage = null;
    }

    /**
     * 构造函数
     *
     * @param success 是否成功
     */
    public BaseResponse(boolean success) {
        this();
        this.success = success;
    }

    /**
     * 构造函数
     *
     * @param success     是否成功
     * @param errorCode   错误码
     * @param errorMessage 错误信息
     */
    public BaseResponse(boolean success, String errorCode, String errorMessage) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 创建成功响应
     *
     * @param <T> 响应类型
     * @return 响应对象
     */
    public static <T extends BaseResponse> T success(T response) {
        if (response != null) {
            response.setSuccess(true);
        }
        return response;
    }

    /**
     * 创建失败响应
     *
     * @param <T>          响应类型
     * @param response     响应对象
     * @param errorMessage 错误信息
     * @return 响应对象
     */
    public static <T extends BaseResponse> T fail(T response, String errorMessage) {
        return fail(response, null, errorMessage);
    }

    /**
     * 创建失败响应
     *
     * @param <T>          响应类型
     * @param response     响应对象
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     * @return 响应对象
     */
    public static <T extends BaseResponse> T fail(T response, String errorCode, String errorMessage) {
        if (response != null) {
            response.setSuccess(false);
            response.setErrorCode(errorCode);
            response.setErrorMessage(errorMessage);
        }
        return response;
    }

    /**
     * 判断是否失败
     *
     * @return 是否失败
     */
    public boolean isFail() {
        return !success;
    }
}
