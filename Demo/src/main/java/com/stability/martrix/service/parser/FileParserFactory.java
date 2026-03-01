package com.stability.martrix.service.parser;

import com.stability.martrix.entity.TroubleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 文件解析器工厂
 *
 * 根据文件内容自动检测平台并选择合适的解析器
 * 支持多平台文件解析（Android、OpenHarmony等）
 */
@Component
public class FileParserFactory {

    private static final Logger logger = LoggerFactory.getLogger(FileParserFactory.class);

    private final List<FileParserStrategy> parsers;

    @Autowired
    public FileParserFactory(List<FileParserStrategy> parsers) {
        // 按优先级排序（数值越小优先级越高）
        this.parsers = new ArrayList<>(parsers);
        this.parsers.sort(Comparator.comparingInt(FileParserStrategy::getPriority));
        logger.info("已加载 {} 个文件解析器: {}",
            parsers.size(),
            parsers.stream().map(FileParserStrategy::getPlatformName).toList()
        );
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

        Optional<FileParserStrategy> parser = findParser(lines);
        if (parser.isEmpty()) {
            logger.warn("未找到能解析该文件的解析器");
            return null;
        }

        FileParserStrategy strategy = parser.get();
        logger.info("使用 {} 解析器处理文件", strategy.getPlatformName());

        TroubleEntity entity = strategy.parse(lines);

        if (entity != null && strategy.isValid(entity)) {
            return entity;
        }

        logger.warn("{} 解析器解析结果无效", strategy.getPlatformName());
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
     * 查找能解析该文件的解析器
     *
     * @param lines 文件内容的行列表
     * @return 找到的解析器（按优先级返回第一个能解析的）
     */
    private Optional<FileParserStrategy> findParser(List<String> lines) {
        for (FileParserStrategy parser : parsers) {
            try {
                if (parser.canParse(lines)) {
                    return Optional.of(parser);
                }
            } catch (Exception e) {
                logger.warn("解析器 {} canParse 检查失败: {}",
                    parser.getPlatformName(), e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * 获取所有已注册的解析器
     *
     * @return 解析器列表
     */
    public List<FileParserStrategy> getAllParsers() {
        return new ArrayList<>(parsers);
    }

    /**
     * 获取指定平台的解析器
     *
     * @param platformName 平台名称
     * @return 对应的解析器，未找到返回 empty
     */
    public Optional<FileParserStrategy> getParserByPlatform(String platformName) {
        return parsers.stream()
            .filter(p -> p.getPlatformName().equalsIgnoreCase(platformName))
            .findFirst();
    }
}
