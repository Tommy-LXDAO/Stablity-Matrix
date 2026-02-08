package com.stability.martrix.constants;

/**
 * 错误码常量
 */
public class ErrorCode {

    /**
     * 成功
     */
    public static final String SUCCESS = "0000";

    /**
     * sessionId为空
     */
    public static final String SESSION_ID_EMPTY = "1001";

    /**
     * 会话不存在
     */
    public static final String SESSION_NOT_FOUND = "1002";

    /**
     * 会话创建失败
     */
    public static final String SESSION_CREATE_FAILED = "1003";

    /**
     * 文件解析失败
     */
    public static final String FILE_PARSE_FAILED = "2001";

    /**
     * 文件存储失败
     */
    public static final String FILE_STORAGE_FAILED = "2002";

    /**
     * 文件类型不支持
     */
    public static final String FILE_TYPE_NOT_SUPPORTED = "2003";

    /**
     * AI分析失败
     */
    public static final String AI_ANALYSIS_FAILED = "3001";

    /**
     * AI调用失败
     */
    public static final String AI_CALL_FAILED = "3002";

    /**
     * AI服务繁忙（HTTP 429）
     */
    public static final String AI_SERVICE_BUSY = "3003";

    /**
     * Tombstone解析失败
     */
    public static final String TOMBSTONE_PARSE_FAILED = "4001";

    /**
     * Tombstone格式无效
     */
    public static final String TOMBSTONE_INVALID_FORMAT = "4002";

    /**
     * ELF文件解析失败
     */
    public static final String ELF_PARSE_FAILED = "4003";

    private ErrorCode() {
        // 私有构造函数，防止实例化
    }
}
