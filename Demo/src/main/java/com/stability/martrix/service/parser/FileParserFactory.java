package com.stability.martrix.service.parser;

import com.stability.martrix.config.ParserProperties;
import com.stability.martrix.entity.TroubleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private static final int TEXT_SAMPLE_SIZE = 4096;
    private static final double UTF16_ZERO_RATIO_THRESHOLD = 0.3;
    private static final double UTF16_ASCII_RATIO_THRESHOLD = 0.5;
    private static final double CONTROL_CHAR_RATIO_THRESHOLD = 0.05;
    private static final double ASCII_TEXT_RATIO_THRESHOLD = 0.2;

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

        try {
            if (!parser.canParse(lines)) {
                logger.debug("{} 解析器判定当前内容不匹配", parser.getPlatformName());
                return null;
            }

            TroubleEntity entity = parser.parse(lines);
            if (entity != null && parser.isValid(entity)) {
                return entity;
            }
        } catch (Exception e) {
            logger.error("{} 解析器处理内容失败", parser.getPlatformName(), e);
            return null;
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
        if (filePath == null) {
            logger.warn("文件路径为空，无法解析");
            return null;
        }

        try {
            List<String> lines = readFileLines(filePath);
            return parseLines(lines);
        } catch (IOException e) {
            logger.error("读取文件失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 读取文件内容并解码为行列表
     *
     * @param filePath 文件路径
     * @return 文件内容的行列表
     * @throws IOException 文件读取或解码失败
     */
    public List<String> readFileLines(Path filePath) throws IOException {
        if (filePath == null) {
            throw new IOException("文件路径为空，无法读取");
        }
        return readLinesWithFallback(filePath);
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

    private List<String> readLinesWithFallback(Path filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        if (fileBytes.length == 0) {
            return List.of();
        }

        if (!isLikelyTextFile(fileBytes)) {
            throw new IOException("文件内容不是可解析的文本格式: " + filePath);
        }

        Exception lastException = null;
        for (Charset charset : getCandidateCharsets(fileBytes)) {
            try {
                List<String> lines = decodeLines(fileBytes, charset);
                if (!StandardCharsets.UTF_8.equals(charset)) {
                    logger.warn("文件 {} 非 UTF-8 编码，回退使用 {} 读取", filePath, charset.name());
                }
                return lines;
            } catch (CharacterCodingException e) {
                lastException = e;
                logger.debug("使用 {} 读取文件 {} 失败", charset.name(), filePath, e);
            }
        }

        throw new IOException("无法识别文件编码: " + filePath, lastException);
    }

    private List<Charset> getCandidateCharsets(byte[] fileBytes) {
        LinkedHashSet<Charset> charsets = new LinkedHashSet<>();

        Charset bomCharset = detectBomCharset(fileBytes);
        if (bomCharset != null) {
            charsets.add(bomCharset);
        }

        Charset bomLessUtf16 = detectBomlessUtf16Charset(fileBytes);
        if (bomLessUtf16 != null) {
            charsets.add(bomLessUtf16);
        }

        charsets.add(StandardCharsets.UTF_8);
        addSupportedCharset(charsets, "GB18030");
        addSupportedCharset(charsets, "GBK");

        return List.copyOf(charsets);
    }

    private void addSupportedCharset(LinkedHashSet<Charset> charsets, String charsetName) {
        if (Charset.isSupported(charsetName)) {
            charsets.add(Charset.forName(charsetName));
        }
    }

    private Charset detectBomCharset(byte[] fileBytes) {
        if (fileBytes.length >= 3
            && (fileBytes[0] & 0xFF) == 0xEF
            && (fileBytes[1] & 0xFF) == 0xBB
            && (fileBytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }

        if (fileBytes.length >= 2) {
            if ((fileBytes[0] & 0xFF) == 0xFF && (fileBytes[1] & 0xFF) == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            if ((fileBytes[0] & 0xFF) == 0xFE && (fileBytes[1] & 0xFF) == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
        }

        return null;
    }

    private Charset detectBomlessUtf16Charset(byte[] fileBytes) {
        int sampleLength = Math.min(fileBytes.length - (fileBytes.length % 2), TEXT_SAMPLE_SIZE);
        if (sampleLength < 16) {
            return null;
        }

        if (looksLikeUtf16(fileBytes, sampleLength, true)) {
            return StandardCharsets.UTF_16LE;
        }

        if (looksLikeUtf16(fileBytes, sampleLength, false)) {
            return StandardCharsets.UTF_16BE;
        }

        return null;
    }

    private boolean looksLikeUtf16(byte[] fileBytes, int sampleLength, boolean littleEndian) {
        int pairs = sampleLength / 2;
        int zeroHighByteCount = 0;
        int zeroLowByteCount = 0;
        int printableLowByteCount = 0;

        for (int i = 0; i < sampleLength; i += 2) {
            int lowByte = fileBytes[littleEndian ? i : i + 1] & 0xFF;
            int highByte = fileBytes[littleEndian ? i + 1 : i] & 0xFF;

            if (highByte == 0) {
                zeroHighByteCount++;
            }
            if (lowByte == 0) {
                zeroLowByteCount++;
            }
            if (isAsciiTextByte(lowByte)) {
                printableLowByteCount++;
            }
        }

        double zeroHighRatio = (double) zeroHighByteCount / pairs;
        double zeroLowRatio = (double) zeroLowByteCount / pairs;
        double printableLowRatio = (double) printableLowByteCount / pairs;

        return zeroHighRatio >= UTF16_ZERO_RATIO_THRESHOLD
            && zeroLowRatio < CONTROL_CHAR_RATIO_THRESHOLD
            && printableLowRatio >= UTF16_ASCII_RATIO_THRESHOLD;
    }

    private boolean isLikelyTextFile(byte[] fileBytes) {
        if (detectBomCharset(fileBytes) != null || detectBomlessUtf16Charset(fileBytes) != null) {
            return true;
        }

        int sampleLength = Math.min(fileBytes.length, TEXT_SAMPLE_SIZE);
        int zeroCount = 0;
        int controlCount = 0;
        int asciiTextCount = 0;

        for (int i = 0; i < sampleLength; i++) {
            int value = fileBytes[i] & 0xFF;
            if (value == 0) {
                zeroCount++;
                continue;
            }

            if (!isTextByte(value)) {
                controlCount++;
            }
            if (isAsciiTextByte(value)) {
                asciiTextCount++;
            }
        }

        return zeroCount == 0
            && ((double) controlCount / sampleLength) <= CONTROL_CHAR_RATIO_THRESHOLD
            && ((double) asciiTextCount / sampleLength) >= ASCII_TEXT_RATIO_THRESHOLD;
    }

    private boolean isTextByte(int value) {
        return value == '\t'
            || value == '\n'
            || value == '\r'
            || value >= 0x20;
    }

    private boolean isAsciiTextByte(int value) {
        return value == '\t'
            || value == '\n'
            || value == '\r'
            || (value >= 0x20 && value <= 0x7E);
    }

    private List<String> decodeLines(byte[] fileBytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        String content = decoder.decode(ByteBuffer.wrap(fileBytes)).toString();
        if (content.indexOf('\u0000') >= 0) {
            throw new CharacterCodingException();
        }
        return toLines(content);
    }

    private List<String> toLines(String content) {
        List<String> lines = new BufferedReader(new StringReader(content))
            .lines()
            .collect(Collectors.toCollection(ArrayList::new));

        if (!lines.isEmpty() && !lines.getFirst().isEmpty() && lines.getFirst().charAt(0) == '\uFEFF') {
            lines.set(0, lines.getFirst().substring(1));
        }

        return lines;
    }
}
