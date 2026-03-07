package com.stability.martrix.service.parser;

import com.stability.martrix.config.ParserProperties;
import com.stability.martrix.entity.TroubleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileParserFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void parseFileShouldDecodeBomlessUtf16LeWithoutCorruptingFields() throws IOException {
        FileParserFactory factory = createFactory(new TombstoneStubParserStrategy());
        Path file = tempDir.resolve("utf16le.log");

        Files.writeString(file, "header\npid: 123\nCmdline: demo进程\n", StandardCharsets.UTF_16LE);

        TroubleEntity entity = factory.parseFile(file);

        assertNotNull(entity);
        assertEquals(123, entity.getPid());
        assertEquals("demo进程", entity.getProcessName());
    }

    @Test
    void parseFileShouldDecodeBomlessUtf16BeWithoutCorruptingFields() throws IOException {
        FileParserFactory factory = createFactory(new TombstoneStubParserStrategy());
        Path file = tempDir.resolve("utf16be.log");

        Files.writeString(file, "header\npid: 456\nCmdline: be进程\n", StandardCharsets.UTF_16BE);

        TroubleEntity entity = factory.parseFile(file);

        assertNotNull(entity);
        assertEquals(456, entity.getPid());
        assertEquals("be进程", entity.getProcessName());
    }

    @Test
    void parseFileShouldFallbackToGb18030WhenUtf8DecodingFails() throws IOException {
        FileParserFactory factory = createFactory(new TombstoneStubParserStrategy());
        Path file = tempDir.resolve("gb18030.log");

        Files.write(file, "备注: 崩溃\npid: 789\nCmdline: gb进程\n".getBytes("GB18030"));

        TroubleEntity entity = factory.parseFile(file);

        assertNotNull(entity);
        assertEquals(789, entity.getPid());
        assertEquals("gb进程", entity.getProcessName());
    }

    @Test
    void parseFileShouldRejectBinaryPayloadEvenIfItContainsMarkers() throws IOException {
        FileParserFactory factory = createFactory(new TombstoneStubParserStrategy());
        Path file = tempDir.resolve("fake-image.bin");

        byte[] payload = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01,
            0x02, 0x03, 0x04, 0x05,
            'p', 'i', 'd', ':', ' ', '1', '2', '3', '\n',
            'C', 'm', 'd', 'l', 'i', 'n', 'e', ':', ' ', 'd', 'e', 'm', 'o', '\n'
        };
        Files.write(file, payload);

        TroubleEntity entity = factory.parseFile(file);

        assertNull(entity);
    }

    @Test
    void readFileLinesShouldThrowWhenPayloadIsBinary() throws IOException {
        FileParserFactory factory = createFactory(new TombstoneStubParserStrategy());
        Path file = tempDir.resolve("fake-image.bin");

        byte[] payload = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01,
            0x02, 0x03, 0x04, 0x05,
            'p', 'i', 'd', ':', ' ', '1', '2', '3', '\n',
            'C', 'm', 'd', 'l', 'i', 'n', 'e', ':', ' ', 'd', 'e', 'm', 'o', '\n'
        };
        Files.write(file, payload);

        assertThrows(IOException.class, () -> factory.readFileLines(file));
    }

    @Test
    void parseFileShouldReturnNullWhenParserThrows() throws IOException {
        FileParserFactory factory = createFactory(new ThrowingParserStrategy());
        Path file = tempDir.resolve("parser-error.log");

        Files.writeString(file, "pid: 789\n", StandardCharsets.UTF_8);

        TroubleEntity entity = factory.parseFile(file);

        assertNull(entity);
    }

    private FileParserFactory createFactory(FileParserStrategy strategy) {
        ParserProperties properties = new ParserProperties();
        properties.setPlatform("android");
        return new FileParserFactory(List.of(strategy), properties);
    }

    private static final class TombstoneStubParserStrategy implements FileParserStrategy {

        @Override
        public String getPlatformName() {
            return "Android";
        }

        @Override
        public boolean canParse(List<String> lines) {
            return lines.stream().anyMatch(line -> line.startsWith("pid:"))
                && lines.stream().anyMatch(line -> line.startsWith("Cmdline:"));
        }

        @Override
        public TroubleEntity parse(List<String> lines) {
            TroubleEntity entity = new TroubleEntity();
            for (String line : lines) {
                if (line.startsWith("pid:")) {
                    entity.setPid(Integer.parseInt(line.substring(4).trim()));
                } else if (line.startsWith("Cmdline:")) {
                    entity.setProcessName(line.substring("Cmdline:".length()).trim());
                }
            }
            return entity;
        }
    }

    private static final class ThrowingParserStrategy implements FileParserStrategy {

        @Override
        public String getPlatformName() {
            return "Android";
        }

        @Override
        public boolean canParse(List<String> lines) {
            return true;
        }

        @Override
        public TroubleEntity parse(List<String> lines) {
            throw new IllegalStateException("parser boom");
        }
    }
}
