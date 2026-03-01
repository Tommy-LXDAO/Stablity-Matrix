package com.stability.martrix.service.parser;

import com.stability.martrix.entity.TroubleEntity;

import java.util.List;

/**
 * 文件解析策略接口
 * 支持多平台文件解析（Android、OpenHarmony等）
 *
 * 使用策略模式，每个平台实现此接口，通过 canParse 方法判断是否能处理该文件
 */
public interface FileParserStrategy {

    /**
     * 获取解析器支持的平台名称
     *
     * @return 平台名称，如 "Android", "OpenHarmony"
     */
    String getPlatformName();

    /**
     * 获取解析器的优先级（数值越小优先级越高）
     * 用于多个解析器都能解析时的选择
     *
     * @return 优先级，默认为 100
     */
    default int getPriority() {
        return 100;
    }

    /**
     * 判断是否能解析该文件内容
     * 通过检测文件特征来判断文件类型
     *
     * @param lines 文件内容的行列表
     * @return true 如果能解析该文件
     */
    boolean canParse(List<String> lines);

    /**
     * 解析文件内容
     *
     * @param lines 文件内容的行列表
     * @return 解析后的 TroubleEntity 对象，解析失败返回 null
     */
    TroubleEntity parse(List<String> lines);

    /**
     * 验证解析结果是否有效
     *
     * @param entity 解析后的实体
     * @return true 如果是有效的解析结果
     */
    default boolean isValid(TroubleEntity entity) {
        if (entity == null) {
            return false;
        }
        // 至少需要有PID信息才算有效
        return entity.getPid() != null;
    }
}
