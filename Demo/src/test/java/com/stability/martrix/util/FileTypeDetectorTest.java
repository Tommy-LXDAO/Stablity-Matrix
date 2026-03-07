package com.stability.martrix.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileTypeDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectFileTypeShouldTreatBomlessUtf16LeTextAsTxt() throws IOException {
        Path file = tempDir.resolve("utf16le.log");
        Files.writeString(file, "pid: 123\nCmdline: demo\n", StandardCharsets.UTF_16LE);

        FileTypeDetector.FileType fileType = FileTypeDetector.detectFileType(file);

        assertEquals(FileTypeDetector.FileType.TXT, fileType);
    }

    @Test
    void detectFileTypeShouldDetectGzipByMagicNumber() throws IOException {
        Path file = tempDir.resolve("payload.bin");
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(file))) {
            gzipOutputStream.write("pid: 1\nCmdline: demo\n".getBytes(StandardCharsets.UTF_8));
        }

        FileTypeDetector.FileType fileType = FileTypeDetector.detectFileType(file);

        assertEquals(FileTypeDetector.FileType.GZIP, fileType);
    }

    @Test
    void detectFileTypeShouldRejectBinaryPayloadWithMarkers() throws IOException {
        Path file = tempDir.resolve("fake-image.bin");
        byte[] payload = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01,
            0x02, 0x03, 0x04, 0x05,
            'p', 'i', 'd', ':', ' ', '1', '2', '3', '\n'
        };
        Files.write(file, payload);

        FileTypeDetector.FileType fileType = FileTypeDetector.detectFileType(file);

        assertEquals(FileTypeDetector.FileType.UNKNOWN, fileType);
    }
}
