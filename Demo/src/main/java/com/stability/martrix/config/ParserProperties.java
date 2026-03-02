package com.stability.martrix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件解析器配置属性
 *
 * 用于配置项目使用的解析器平台类型
 * 在 application.yaml 中通过 parser.platform 配置
 */
@Component
@ConfigurationProperties(prefix = "parser")
public class ParserProperties {

    /**
     * 解析器平台类型
     * 支持的值: android, openharmony
     * 默认使用 android
     */
    private String platform = "android";

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
