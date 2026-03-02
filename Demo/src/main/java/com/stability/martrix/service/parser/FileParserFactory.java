package com.stability.martrix.service.parser;

import com.stability.martrix.config.ParserProperties;
import com.stability.martrix.entity.TroubleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件解析器工厂
 *
 * 根据配置选择合适的解析器
 * 支持多平台文件解析（Android、OpenHarmony等）
 *
 * 解析器在项目启动时通过配置指定，无需运行时判断
 */
@Component
public class FileParserFactory {

    private static final Logger logger = LoggerFactory.getLogger(FileParserFactory.class);

    private final FileParserStrategy parser;
    private final Map<String, FileParserStrategy> parserMap;

    /**
     * 构造函数 - 根据配置选择解析器
     *
     * @param parsers 所有可用的解析器
     * @param properties 解析器配置属性
     */
    public FileParserFactory(List<FileParserStrategy> parsers, ParserProperties properties) {
        // 构建平台名称到解析器的映射
        this.parserMap = parsers.stream()
            .collect(Collectors.toMap(
                p -> p.getPlatformName().toLowerCase(),
                Function.identity(),
                (existing, ignored) -> existing
            ));

        // 根据配置选择解析器
        String configuredPlatform = properties.getPlatform().toLowerCase();
        this.parser = selectParser(configuredPlatform);

        logger.info("文件解析器初始化完成 - 配置平台: {}, 实际使用: {}, 可用解析器: {}",
            configuredPlatform,
            parser.getPlatformName(),
            parserMap.keySet()
        );
    }

    /**
     * 根据配置选择解析器
     */
    private FileParserStrategy selectParser(String platform) {
        FileParserStrategy selected = parserMap.get(platform);

        if (selected == null) {
            logger.warn("未找到配置的解析器平台: {}, 使用默认解析器", platform);
            // 尝试使用 android 作为默认
            selected = parserMap.get("android");
            if (selected == null && !parserMap.isEmpty()) {
                selected = parserMap.values().iterator().next();
            }
        }

        if (selected == null) {
            throw new IllegalStateException("没有可用的文件解析器");
        }

        return selected;
    }

    /**
     * 解析文件内容
     *
     * @param lines 文件内容的行列表
     * @return 解析后的 TroubleEntity 对象，解析失败返回 null
     */
    public TroubleEntity parseLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        logger.debug("使用 {} 解析器处理文件", parser.getPlatformName());

        TroubleEntity entity = parser.parse(lines);

        if (entity != null && parser.isValid(entity)) {
            return entity;
        }

        logger.warn("{} 解析器解析结果无效", parser.getPlatformName());
        return null;
    }

    /**
     * 解析文件
     *
     * @param filePath 文件路径
     * @return 解析后的 TroubleEntity 对象，解析失败返回 null
     */
    public TroubleEntity parseFile(Path filePath) {
        try {
            List<String> lines = Files.readAllLines(filePath);
            return parseLines(lines);
        } catch (Exception e) {
            logger.error("读取文件失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 获取当前使用的解析器
     *
     * @return 当前解析器
     */
    public FileParserStrategy getCurrentParser() {
        return parser;
    }

    /**
     * 获取指定平台的解析器
     *
     * @param platformName 平台名称
     * @return 对应的解析器，未找到返回 empty
     */
    public Optional<FileParserStrategy> getParserByPlatform(String platformName) {
        return Optional.ofNullable(parserMap.get(platformName.toLowerCase()));
    }

    /**
     * 获取所有已注册的解析器平台名称
     *
     * @return 平台名称列表
     */
    public List<String> getAvailablePlatforms() {
        return List.copyOf(parserMap.keySet());
    }
}
