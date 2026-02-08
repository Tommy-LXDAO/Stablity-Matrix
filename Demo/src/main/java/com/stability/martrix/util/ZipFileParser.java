package com.stability.martrix.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP文件解析工具
 */
public class ZipFileParser {

    /**
     * ZIP文件条目信息
     */
    public static class ZipEntryInfo {
        private String name;
        private long size;
        private boolean isDirectory;
        private byte[] content;

        public ZipEntryInfo(String name, long size, boolean isDirectory) {
            this.name = name;
            this.size = size;
            this.isDirectory = isDirectory;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public void setDirectory(boolean directory) {
            isDirectory = directory;
        }

        public byte[] getContent() {
            return content;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }
    }

    /**
     * 解析ZIP文件中的所有条目
     *
     * @param zipFile ZIP文件
     * @return 条目列表
     */
    public static List<ZipEntryInfo> parseZipEntries(MultipartFile zipFile) throws IOException {
        List<ZipEntryInfo> entries = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ZipEntryInfo entryInfo = new ZipEntryInfo(
                    entry.getName(),
                    entry.getSize(),
                    entry.isDirectory()
                );

                if (!entry.isDirectory()) {
                    // 读取文件内容
                    byte[] content = readEntryContent(zis);
                    entryInfo.setContent(content);
                }

                entries.add(entryInfo);
                zis.closeEntry();
            }
        }

        return entries;
    }

    /**
     * 读取ZIP条目内容
     */
    private static byte[] readEntryContent(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    /**
     * 查找ZIP中可能的Tombstone文件
     * Tombstone文件通常包含"tombstone"关键字，或以"tombstone_"开头
     *
     * @param zipFile ZIP文件
     * @return 可能的Tombstone文件内容列表
     */
    public static List<ZipEntryInfo> findPotentialTombstoneFiles(MultipartFile zipFile) throws IOException {
        List<ZipEntryInfo> result = new ArrayList<>();
        List<ZipEntryInfo> entries = parseZipEntries(zipFile);

        for (ZipEntryInfo entry : entries) {
            if (entry.isDirectory()) {
                continue;
            }

            String name = entry.getName().toLowerCase();
            // 检查文件名是否像tombstone
            if (name.contains("tombstone") ||
                name.startsWith("tombstone_") ||
                name.endsWith(".txt") && name.contains("crash")) {

                result.add(entry);
            } else {
                // 检查内容是否像tombstone
                if (entry.getContent() != null && entry.getContent().length > 0) {
                    String content = new String(entry.getContent());
                    if (content.contains("pid:") && content.contains("signal")) {
                        result.add(entry);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 查找ZIP中可能的ELF文件
     *
     * @param zipFile ZIP文件
     * @return 可能的ELF文件信息列表
     */
    public static List<ZipEntryInfo> findPotentialElfFiles(MultipartFile zipFile) throws IOException {
        List<ZipEntryInfo> result = new ArrayList<>();
        List<ZipEntryInfo> entries = parseZipEntries(zipFile);

        for (ZipEntryInfo entry : entries) {
            if (entry.isDirectory()) {
                continue;
            }

            // 检查文件名
            String name = entry.getName().toLowerCase();
            if (name.endsWith(".so") || name.endsWith(".elf")) {
                result.add(entry);
            } else {
                // 检查魔数
                if (entry.getContent() != null && entry.getContent().length >= 4) {
                    byte[] header = entry.getContent();
                    if (header[0] == 0x7F && header[1] == 'E' &&
                        header[2] == 'L' && header[3] == 'F') {
                        result.add(entry);
                    }
                }
            }
        }

        return result;
    }
}
