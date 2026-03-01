package com.stability.martrix.service.parser;

import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.entity.register.AArch64RegisterDumpInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Android AArch64 Tombstone 文件解析器
 *
 * 解析 Android 系统的 tombstone 崩溃日志文件
 * 支持解析：Cmdline、PID/TID、Signal 信息、Backtrace 堆栈回溯、寄存器 dump 等
 */
@Component
public class AndroidTombstoneParser implements FileParserStrategy {

    private static final Logger logger = Logger.getLogger(AndroidTombstoneParser.class.getName());

    /**
     * Android tombstone 文件的典型特征
     */
    private static final String[] TOMBSTONE_MARKERS = {
        "BuildId:",
        "backtrace:",
        "signal ",
        "pid:",
        "Cmdline:"
    };

    @Override
    public String getPlatformName() {
        return "Android";
    }

    @Override
    public int getPriority() {
        return 10;  // Android 解析器优先级较高
    }

    @Override
    public boolean canParse(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }

        // 检查文件前 50 行是否包含 tombstone 特征
        int checkLines = Math.min(50, lines.size());
        int matchCount = 0;

        for (int i = 0; i < checkLines; i++) {
            String line = lines.get(i);
            for (String marker : TOMBSTONE_MARKERS) {
                if (line.contains(marker)) {
                    matchCount++;
                    break;
                }
            }
        }

        // 至少匹配 2 个特征才认为是 tombstone 文件
        return matchCount >= 2;
    }

    @Override
    public TroubleEntity parse(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        AArch64Tombstone tombstone = new AArch64Tombstone();
        parseLines(lines, tombstone);

        return tombstone;
    }

    @Override
    public boolean isValid(TroubleEntity entity) {
        if (!(entity instanceof AArch64Tombstone)) {
            return false;
        }
        AArch64Tombstone tombstone = (AArch64Tombstone) entity;
        // 至少需要有PID或Signal信息才算有效
        return tombstone.getPid() != null || tombstone.getSignalInfo() != null;
    }

    /**
     * 解析行信息并填充到 tombstone 对象中
     */
    private void parseLines(List<String> lines, AArch64Tombstone tombstone) {
        List<AArch64Tombstone.FdInfo> fdInfos = new ArrayList<>();
        boolean backtraceParsed = false;
        boolean registerParsed = false;
        boolean pidParsed = false;
        boolean specialRegisterParsed = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Cmdline:")) {
                parseCmdlineLine(line, tombstone);
            } else if (!pidParsed && line.startsWith("pid:")) {
                pidParsed = true;
                parsePidLine(line, tombstone);
            } else if (line.startsWith("signal ")) {
                tombstone.setSignalInfo(parseSignalInfo(line));
            } else if (!backtraceParsed && line.startsWith("backtrace:")) {
                backtraceParsed = true;
                tombstone.setStackDumpInfo(parseBacktraceInfo(lines, i));
            } else if (!registerParsed && line.contains("x0  ")) {
                registerParsed = true;
                tombstone.setRegisterDumpInfo(parseRegisterInfo(lines, i));
            } else if (!specialRegisterParsed && line.contains("lr ")) {
                specialRegisterParsed = true;
                AArch64Tombstone.SpecialRegisterInfo specialRegisterInfo = parseSpecialRegisterInfo(lines, i);
                if (specialRegisterInfo != null) {
                    tombstone.setSpecialRegisterInfo(specialRegisterInfo);
                }
            } else if (line.contains("open files:")) {
                fdInfos = parseFdInfo(lines, i);
            } else if (line.contains("Maps:")) {
                // 解析内存映射信息（预留）
            }
        }

        if (!fdInfos.isEmpty()) {
            tombstone.setFdInfo(fdInfos);
        }
    }

    private void parseCmdlineLine(String line, AArch64Tombstone tombstone) {
        String[] parts = line.split(": ", 2);
        if (parts.length >= 2) {
            tombstone.setProcessName(parts[1].trim());
        }
    }

    private void parsePidLine(String line, AArch64Tombstone tombstone) {
        String[] parts = line.split(",");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith("pid:")) {
                tombstone.setPid(Integer.parseInt(trimmedPart.substring(4).trim()));
            } else if (trimmedPart.startsWith("tid:")) {
                tombstone.setFirstTid(Integer.parseInt(trimmedPart.substring(4).trim()));
            } else if (trimmedPart.startsWith("name:")) {
                if (tombstone.getProcessName() == null || tombstone.getProcessName().isEmpty()) {
                    String namePart = trimmedPart.substring(5).trim();
                    if (namePart.contains(">>>") && namePart.contains("<<<")) {
                        int start = namePart.indexOf(">>>") + 3;
                        int end = namePart.indexOf("<<<");
                        if (start < end) {
                            tombstone.setProcessName(namePart.substring(start, end).trim());
                        }
                    } else {
                        tombstone.setProcessName(namePart);
                    }
                }
            }
        }
    }

    private AArch64Tombstone.SignalInfo parseSignalInfo(String line) {
        String[] s = line.split(" ");
        if (s.length < 8) {
            throw new RuntimeException("解析信号信息失败，字符串长度" + s.length + " 字符串信息:" + line);
        }
        AArch64Tombstone.SignalInfo signalInfo = new AArch64Tombstone.SignalInfo(Integer.parseInt(s[1]));
        signalInfo.setSigInformation(s[2].replaceAll("[(),]", ""));
        signalInfo.setTroubleInformation(s[5].replaceAll("[(),]", ""));
        signalInfo.setFaultAddress(parseHexAddress(s[8]));

        if ("fault".equals(s[7]) && s.length > 8) {
            signalInfo.setFaultAddress(parseHexAddress(s[8]));
        }

        return signalInfo;
    }

    private Long parseHexAddress(String hexStr) {
        if (hexStr == null || "null".equals(hexStr)) {
            return null;
        }
        try {
            if (hexStr.startsWith("0x")) {
                return Long.parseLong(hexStr.substring(2), 16);
            } else {
                return Long.parseLong(hexStr, 16);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private AArch64Tombstone.StackDumpInfo parseBacktraceInfo(List<String> lines, int startIndex) {
        AArch64Tombstone.StackDumpInfo stackDumpInfo = new AArch64Tombstone.StackDumpInfo();
        List<AArch64Tombstone.StackDumpInfo.StackFrame> stackFrames = new ArrayList<>();

        for (int i = startIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || !line.startsWith("#")) {
                break;
            }

            String[] parts = line.split("\\s+");
            if (parts.length < 4) {
                continue;
            }

            int index = Integer.parseInt(parts[0].substring(1));
            Long address = null;
            try {
                String pcStr = parts[2];
                if (pcStr.startsWith("0x")) {
                    address = Long.parseLong(pcStr.substring(2), 16);
                } else {
                    address = Long.parseLong(pcStr, 16);
                }
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }

            String mapsInfo = parts[3];
            String symbol = null;
            Long offsetFromSymbolStart = null;
            String buildId = null;
            AArch64Tombstone.StackDumpInfo.StackFrame.AddressType addressType =
                AArch64Tombstone.StackDumpInfo.StackFrame.AddressType.OFFSET;

            StringBuilder fullInfoBuilder = new StringBuilder();
            for (int j = 4; j < parts.length; j++) {
                if (j > 4) fullInfoBuilder.append(" ");
                fullInfoBuilder.append(parts[j]);
            }

            String fullInfo = fullInfoBuilder.toString();

            int buildIdStart = -1;
            int buildIdEnd = -1;

            int tempBuildIdStart = fullInfo.indexOf("(BuildId: ");
            if (tempBuildIdStart != -1) {
                buildIdEnd = fullInfo.indexOf(")", tempBuildIdStart);
                if (buildIdEnd != -1) {
                    buildIdStart = tempBuildIdStart;
                }
            }

            if (buildIdStart != -1 && buildIdEnd != -1) {
                buildId = fullInfo.substring(buildIdStart + 10, buildIdEnd).trim();
                String symbolPart = fullInfo.substring(0, buildIdStart).trim();

                if (symbolPart.startsWith("(") && symbolPart.endsWith(")")) {
                    symbolPart = symbolPart.substring(1, symbolPart.length() - 1);
                }

                if (!symbolPart.isEmpty()) {
                    int lastPlusIndex = symbolPart.lastIndexOf('+');
                    if (lastPlusIndex != -1) {
                        String potentialSymbol = symbolPart.substring(0, lastPlusIndex).trim();
                        String offsetStr = symbolPart.substring(lastPlusIndex + 1).trim();

                        try {
                            offsetFromSymbolStart = Long.parseLong(offsetStr);
                            symbol = potentialSymbol;
                        } catch (NumberFormatException e) {
                            symbol = symbolPart;
                        }
                    } else {
                        symbol = symbolPart;
                    }
                }
            } else {
                String symbolPart = fullInfo;

                if (symbolPart.startsWith("(") && symbolPart.endsWith(")")) {
                    symbolPart = symbolPart.substring(1, symbolPart.length() - 1);
                }

                if (!symbolPart.isEmpty()) {
                    int lastPlusIndex = symbolPart.lastIndexOf('+');
                    if (lastPlusIndex != -1) {
                        String potentialSymbol = symbolPart.substring(0, lastPlusIndex).trim();
                        String offsetStr = symbolPart.substring(lastPlusIndex + 1).trim();

                        try {
                            offsetFromSymbolStart = Long.parseLong(offsetStr);
                            symbol = potentialSymbol;
                        } catch (NumberFormatException e) {
                            symbol = symbolPart;
                        }
                    } else {
                        symbol = symbolPart;
                    }
                }
            }

            AArch64Tombstone.StackDumpInfo.StackFrame stackFrame =
                new AArch64Tombstone.StackDumpInfo.StackFrame(
                    offsetFromSymbolStart,
                    symbol,
                    mapsInfo,
                    addressType,
                    address,
                    index,
                    buildId
                );

            stackFrames.add(stackFrame);
        }

        stackDumpInfo.setStackFrames(stackFrames);
        return stackDumpInfo;
    }

    private AArch64RegisterDumpInfo parseRegisterInfo(List<String> lines, int startIndex) {
        AArch64RegisterDumpInfo registerDumpInfo = new AArch64RegisterDumpInfo();

        for (int i = startIndex; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("backtrace:") || line.contains("memory near")) {
                break;
            }

            String[] registers = line.split("\\s+");
            for (int j = 0; j < registers.length - 1; j += 2) {
                String regName = registers[j];
                String regValueStr = registers[j + 1];

                try {
                    long regValue = 0;
                    if (regValueStr.startsWith("0x")) {
                        regValue = Long.parseLong(regValueStr.substring(2), 16);
                    } else {
                        regValue = Long.parseLong(regValueStr, 16);
                    }

                    setRegisterValue(registerDumpInfo, regName, regValue);
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }
            }
        }

        return registerDumpInfo;
    }

    private void setRegisterValue(AArch64RegisterDumpInfo info, String regName, long value) {
        switch (regName) {
            case "x0" -> info.setX0(value);
            case "x1" -> info.setX1(value);
            case "x2" -> info.setX2(value);
            case "x3" -> info.setX3(value);
            case "x4" -> info.setX4(value);
            case "x5" -> info.setX5(value);
            case "x6" -> info.setX6(value);
            case "x7" -> info.setX7(value);
            case "x8" -> info.setX8(value);
            case "x9" -> info.setX9(value);
            case "x10" -> info.setX10(value);
            case "x11" -> info.setX11(value);
            case "x12" -> info.setX12(value);
            case "x13" -> info.setX13(value);
            case "x14" -> info.setX14(value);
            case "x15" -> info.setX15(value);
            case "x16" -> info.setX16(value);
            case "x17" -> info.setX17(value);
            case "x18" -> info.setX18(value);
            case "x19" -> info.setX19(value);
            case "x20" -> info.setX20(value);
            case "x21" -> info.setX21(value);
            case "x22" -> info.setX22(value);
            case "x23" -> info.setX23(value);
            case "x24" -> info.setX24(value);
            case "x25" -> info.setX25(value);
            case "x26" -> info.setX26(value);
            case "x27" -> info.setX27(value);
            case "x28" -> info.setX28(value);
            case "x29" -> info.setX29(value);
            case "lr" -> info.setX30(value);
            case "sp" -> info.setSp(value);
            case "pc" -> info.setPc(value);
        }
    }

    private AArch64Tombstone.SpecialRegisterInfo parseSpecialRegisterInfo(List<String> lines, int startIndex) {
        String line = lines.get(startIndex).trim();
        Long lr = null, sp = null, pc = null, pst = null;
        boolean flag = false;

        String[] parts = line.split("\\s+");
        for (int i = 0; i < parts.length - 1; i += 2) {
            String regName = parts[i];
            String regValueStr = parts[i + 1];

            try {
                long regValue = 0;
                if (regValueStr.startsWith("0x")) {
                    regValue = Long.parseLong(regValueStr.substring(2), 16);
                } else {
                    regValue = Long.parseLong(regValueStr, 16);
                }

                switch (regName) {
                    case "lr" -> { lr = regValue; flag = true; }
                    case "sp" -> { sp = regValue; flag = true; }
                    case "pc" -> { pc = regValue; flag = true; }
                    case "pst" -> { pst = regValue; flag = true; }
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (!flag) {
            return null;
        }

        return new AArch64Tombstone.SpecialRegisterInfo(lr, sp, pc, pst);
    }

    private List<AArch64Tombstone.FdInfo> parseFdInfo(List<String> lines, int startIndex) {
        List<AArch64Tombstone.FdInfo> fdInfos = new ArrayList<>();

        for (int i = startIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.isEmpty() ||
                line.startsWith("Maps:") ||
                line.startsWith("memory map") ||
                line.startsWith("***")) {
                break;
            }

            if (line.startsWith("fd ")) {
                try {
                    int colonIndex = line.indexOf(':');
                    if (colonIndex > 0) {
                        String fdPart = line.substring(3, colonIndex).trim();
                        int fd = Integer.parseInt(fdPart);

                        String remaining = line.substring(colonIndex + 1).trim();
                        int parenIndex = remaining.lastIndexOf(" (");
                        if (parenIndex > 0) {
                            String path = remaining.substring(0, parenIndex).trim();
                            String fdsanInfoStr = remaining.substring(parenIndex).trim();

                            AArch64Tombstone.FdInfo.FdsanInfo fdsanInfo =
                                AArch64Tombstone.FdInfo.parseFdsanInfo(fdsanInfoStr);
                            fdInfos.add(new AArch64Tombstone.FdInfo(fd, path, fdsanInfo));
                        } else {
                            fdInfos.add(new AArch64Tombstone.FdInfo(fd, remaining, null));
                        }
                    }
                } catch (Exception e) {
                    logger.warning("解析Fd信息失败: " + line + ", 错误: " + e.getMessage());
                }
            }
        }

        return fdInfos;
    }
}
