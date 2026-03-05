package com.stability.martrix.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用llvm-addr2line获取二进制地址对应的源代码行号工具类
 */
public class Addr2LineExtractor {

    private static final Logger logger = LoggerFactory.getLogger(Addr2LineExtractor.class);

    /**
     * 地址解析结果
     */
    public static class AddressInfo {
        private final String address;
        private final String functionName;
        private final String filePath;
        private final int lineNumber;
        private final int columnNumber;

        public AddressInfo(String address, String functionName, String filePath, int lineNumber, int columnNumber) {
            this.address = address;
            this.functionName = functionName;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }

        public String getAddress() {
            return address;
        }

        public String getFunctionName() {
            return functionName;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public int getColumnNumber() {
            return columnNumber;
        }

        @Override
        public String toString() {
            return "AddressInfo{address='" + address + "', functionName='" + functionName +
                    "', filePath='" + filePath + "', lineNumber=" + lineNumber + ", columnNumber=" + columnNumber + "}";
        }
    }

    /**
     * 根据单个地址获取源代码位置
     *
     * @param address 十六进制地址（如 "0x7f8a9b2c4d" 或 "7f8a9b2c4d"）
     * @param soFilePath .so文件或可执行文件路径
     * @return 地址解析结果，如果解析失败返回null
     */
    public static AddressInfo getAddressInfo(String address, String soFilePath) {
        // 确保地址格式正确
        String addr = address.startsWith("0x") || address.startsWith("0X") ? address : "0x" + address;

        try {
            ProcessBuilder pb = new ProcessBuilder("llvm-addr2line", "-Cfie", soFilePath, addr);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("llvm-addr2line执行失败，退出码: {}, 地址: {}, 文件: {}", exitCode, addr, soFilePath);
                return null;
            }

            return parseOutput(addr, output.toString());
        } catch (IOException | InterruptedException e) {
            logger.error("获取地址信息失败: {} @ {}", addr, soFilePath, e);
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 根据单个地址获取源代码位置（纯地址，不带0x前缀）
     *
     * @param address 十六进制地址（不带前缀）
     * @param soFilePath .so文件或可执行文件路径
     * @return 地址解析结果，如果解析失败返回null
     */
    public static AddressInfo getAddressInfoNoPrefix(String address, String soFilePath) {
        return getAddressInfo(address, soFilePath);
    }

    /**
     * 批量解析多个地址
     *
     * @param addresses 地址列表
     * @param soFilePath .so文件或可执行文件路径
     * @return 地址解析结果列表
     */
    public static List<AddressInfo> getAddressInfos(List<String> addresses, String soFilePath) {
        List<AddressInfo> results = new ArrayList<>();

        // 准备地址列表，每行一个地址
        StringBuilder addrList = new StringBuilder();
        for (String addr : addresses) {
            String addrStr = addr.startsWith("0x") || addr.startsWith("0X") ? addr : "0x" + addr;
            addrList.append(addrStr).append("\n");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("llvm-addr2line", "-Cfie", soFilePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 写入地址列表
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write(addrList.toString());
                writer.flush();
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("llvm-addr2line批量执行失败，退出码: {}", exitCode);
                return results;
            }

            // 解析输出
            String[] lines = output.toString().split("\n");
            int addrIndex = 0;
            for (int i = 0; i < lines.length && addrIndex < addresses.size(); i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }
                // 每两个非空行对应一个地址（函数名 + 文件位置）
                StringBuilder entryOutput = new StringBuilder();
                entryOutput.append(line).append("\n");
                if (i + 1 < lines.length) {
                    entryOutput.append(lines[i + 1]).append("\n");
                }
                AddressInfo info = parseOutput(addresses.get(addrIndex), entryOutput.toString());
                if (info != null) {
                    results.add(info);
                }
                addrIndex++;
                i++; // 跳过下一行
            }
        } catch (IOException | InterruptedException e) {
            logger.error("批量获取地址信息失败: {}", soFilePath, e);
            Thread.currentThread().interrupt();
        }

        return results;
    }

    /**
     * 解析llvm-addr2line输出
     * 输出格式：
     * function_name
     * /path/to/file.c:123:0
     */
    private static AddressInfo parseOutput(String address, String output) {
        String[] lines = output.split("\n");
        String functionName = null;
        String filePath = null;
        int lineNumber = 0;
        int columnNumber = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 检查是否为文件路径行（包含 :行号:列号）
            Pattern filePattern = Pattern.compile("(.*?):(\\d+):(\\d+)");
            Matcher fileMatcher = filePattern.matcher(line);
            if (fileMatcher.matches()) {
                filePath = fileMatcher.group(1);
                lineNumber = Integer.parseInt(fileMatcher.group(2));
                columnNumber = Integer.parseInt(fileMatcher.group(3));
                continue;
            }

            // 如果不是文件路径行，则可能是函数名
            if (line.startsWith("(") && line.endsWith(")")) {
                // 跳过一些特殊标记
                continue;
            }
            if (functionName == null && !line.contains(" at ") && !line.contains(" in ")) {
                functionName = line;
            }
        }

        // 如果没有解析到有效信息，返回null
        if (filePath == null || lineNumber == 0) {
            // 检查是否是 "??" 未知地址
            if (output.contains("??") && output.contains(":0:0")) {
                logger.debug("地址解析结果未知: {}", address);
                return null;
            }
            logger.warn("无法解析地址信息: {}", address);
            return null;
        }

        return new AddressInfo(address, functionName, filePath, lineNumber, columnNumber);
    }
}
