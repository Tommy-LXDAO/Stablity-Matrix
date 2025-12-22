package com.stability.martrix.service;

import com.stability.martrix.annotation.AndroidAArch64Demo;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.entity.register.AArch64RegisterDumpInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@AndroidAArch64Demo
@Service
public class AndroidAArch64FileService implements FileService {
    private static final Logger logger = Logger.getLogger(AndroidAArch64FileService.class.getName());
    
    // 注入资源读取服务
    private final ResourceReaderService resourceReaderService;
    
    @Autowired
    public AndroidAArch64FileService(ResourceReaderService resourceReaderService) {
        this.resourceReaderService = resourceReaderService;
    }
    
    // 默认构造函数，保持向后兼容性
    public AndroidAArch64FileService() {
        this.resourceReaderService = null;
    }

    private boolean checkABI(List<String> strings) {
        for (String string : strings) {
            if (string.contains("ABI:") && string.contains("arm64")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TroubleEntity parseFile(String filePath) {
        AArch64Tombstone tombstone = new AArch64Tombstone();
        
        // 1. 查找文件
        if (!checkFileExists(filePath)) {
            logger.info("文件不存在: " + filePath);
            return tombstone;
        }
        
        // 2. 获取行列表
        List<String> lines = readLinesFromFile(filePath);
        if (lines == null) {
            return tombstone;
        }
        
        // 3. 解析行信息
        parseLines(lines, tombstone);
        
        return tombstone;
    }
    
    @Override
    public TroubleEntity parseFile(List<String> lines) {
        AArch64Tombstone tombstone = new AArch64Tombstone();
        
        // 直接解析行信息
        if (lines != null && !lines.isEmpty()) {
            parseLines(lines, tombstone);
        }
        
        return tombstone;
    }
    
    /**
     * 从Spring Resource路径解析文件
     * @param resourcePath 资源路径
     * @return 解析后的ToubleEntity对象
     */
    public TroubleEntity parseResourceFile(String resourcePath) {
        AArch64Tombstone tombstone = new AArch64Tombstone();
        
        // 1. 获取行列表
        List<String> lines = readLinesFromResource(resourcePath);
        if (lines == null || lines.isEmpty()) {
            logger.info("无法从资源路径读取文件内容: " + resourcePath);
            return tombstone;
        }
        
        // 2. 解析行信息
        parseLines(lines, tombstone);
        
        return tombstone;
    }
    
    /**
     * 检查文件是否存在
     */
    private boolean checkFileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
    
    /**
     * 从文件系统中读取所有行
     */
    private List<String> readLinesFromFile(String filePath) {
        try {
            return Files.readAllLines(Paths.get(filePath));
        } catch (IOException e) {
            logger.info("文件" + filePath + "打开失败" + e.getMessage());
            return null;
        }
    }
    
    /**
     * 从Spring Resource中读取所有行
     */
    private List<String> readLinesFromResource(String resourcePath) {
        if (resourceReaderService != null) {
            return resourceReaderService.readLinesFromResource(resourcePath);
        } else {
            logger.warning("ResourceReaderService未注入，无法读取资源文件: " + resourcePath);
            return new ArrayList<>();
        }
    }
    
    /**
     * 解析行信息并填充到tombstone对象中
     */
    private void parseLines(List<String> lines, AArch64Tombstone tombstone) {
        // 用于收集Fd信息
        List<AArch64Tombstone.FdInfo> fdInfos = new ArrayList<>();
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("Cmdline:")) {
                parseCmdlineLine(line, tombstone);
            } else if (line.startsWith("pid:")) {
                parsePidLine(line, tombstone);
            } else if (line.startsWith("signal ")) {
                tombstone.setSignalInfo(parseSignalInfo(line));
            } else if (line.startsWith("backtrace:")) {
                // 解析堆栈回溯信息
                tombstone.setStackDumpInfo(parseBacktraceInfo(lines, i));
            } else if (line.contains("x0  ")) {
                // 解析寄存器信息
                tombstone.setRegisterDumpInfo(parseRegisterInfo(lines, i));
            } else if (line.contains("lr ")) {
                // 解析特殊寄存器信息:     lr  0000007d0f7a7fb8  sp  0000007bdab4f9a0  pc  0000007d0f79d8cc  pst 0000000060001000
                AArch64Tombstone.SpecialRegisterInfo specialRegisterInfo = parseSpecialRegisterInfo(lines, i);
                if (specialRegisterInfo != null) tombstone.setSpecialRegisterInfo(specialRegisterInfo);

            } else if (line.contains("open files:")) {
                // 解析文件描述符信息
                fdInfos = parseFdInfo(lines, i);
            } else if (line.contains("Maps:")) {
                // 解析内存映射信息

            }
        }
        
        // 设置Fd信息
        if (!fdInfos.isEmpty()) {
            tombstone.setFdInfo(fdInfos);
        }
    }
    
    /**
     * 解析Cmdline行
     */
    private void parseCmdlineLine(String line, AArch64Tombstone tombstone) {
        String[] parts = line.split(": ", 2);
        if (parts.length >= 2) {
            tombstone.setProcessName(parts[1].trim());
        }
    }
    
    /**
     * 解析pid行
     */
    private void parsePidLine(String line, AArch64Tombstone tombstone) {
        // 解析 pid, tid, name 等信息
        // 示例: pid: 16369, tid: 16369, name: pool-1-temporar  >>> com.apkpure.aegon <<<
        String[] parts = line.split(",");
        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith("pid:")) {
                tombstone.setPid(Integer.parseInt(trimmedPart.substring(4).trim()));
            } else if (trimmedPart.startsWith("tid:")) {
                tombstone.setFirstTid(Integer.parseInt(trimmedPart.substring(4).trim()));
            } else if (trimmedPart.startsWith("name:")) {
                // 可选：设置进程名（如果Cmdline没有提供的话）
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
            throw new RuntimeException("解析信号信息失败，字符串长度"+s.length+" 字符串信息:"+line);
        }
        // 解析 signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x7c5072d048
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
        // 解析堆栈回溯信息
        // 示例: #00 pc 000000000005a8cc  /system/lib64/libbinder.so (android::Parcel::ipcSetDataReference(unsigned char const*, unsigned long, unsigned long long const*, unsigned long, void (*)(android::Parcel*, unsigned char const*, unsigned long, unsigned long long const*, unsigned long))+340) (BuildId: f992d84feb3f8b8e5f0f7268aeaa2f5d)
        List<AArch64Tombstone.StackDumpInfo.StackFrame> stackFrames = new ArrayList<>();
        
        for (int i = startIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || !line.startsWith("#")) {
                break;
            }
            
            // 解析每一行堆栈信息
            String[] parts = line.split("\\s+", 5); // 最多分成5部分
            if (parts.length >= 4) {
                int index = Integer.parseInt(parts[0].substring(1)); // 移除 # 前缀
                
                Long address = null;
                if (parts.length > 2) {
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
                }
                
                String symbol = null;
                String mapsInfo = null;
                AArch64Tombstone.StackDumpInfo.StackFrame.AddressType addressType = AArch64Tombstone.StackDumpInfo.StackFrame.AddressType.OFFSET;
                if (parts.length >= 5) {
                    mapsInfo = parts[3]; // 共享库路径
                    symbol = parts[4];   // 符号信息（函数名等）
                    
                    // 清理符号信息，移除括号
                    if (symbol.startsWith("(") && symbol.endsWith(")")) {
                        symbol = symbol.substring(1, symbol.length() - 1);
                    }
                    
                    // 如果符号信息实际上是 BuildId，则将其置为 null
                    // BuildId 格式类似于: BuildId: f992d84feb3f8b8e5f0f7268aeaa2f5d
                    if (symbol.startsWith("BuildId:")) {
                        symbol = null;
                        // 如果只有BuildId信息，没有符号信息，则地址类型为绝对地址
                        addressType = AArch64Tombstone.StackDumpInfo.StackFrame.AddressType.ABSOLUTE;
                    }
                } else if (parts.length >= 4) {
                    mapsInfo = parts[3];
                    // 如果只有映射信息但没有符号信息，则地址类型为绝对地址
                    addressType = AArch64Tombstone.StackDumpInfo.StackFrame.AddressType.ABSOLUTE;
                }
                
                AArch64Tombstone.StackDumpInfo.StackFrame stackFrame = new AArch64Tombstone.StackDumpInfo.StackFrame(
                    null, // offsetFromSymbolStart
                    symbol, // symbol
                    mapsInfo, // mapsInfo
                    addressType, // addressType
                    address, // address
                    index // index
                );
                
                stackFrames.add(stackFrame);
            }
        }
        
        stackDumpInfo.setStackFrames(stackFrames);
        return stackDumpInfo;
    }
    
    private AArch64RegisterDumpInfo parseRegisterInfo(List<String> lines, int startIndex) {
        AArch64RegisterDumpInfo registerDumpInfo = new AArch64RegisterDumpInfo();
        // 解析寄存器信息
        // 示例: x0  0000000000000000  x1  0000007c5072d028  x2  0000000000000020  x3  0000007c5072d048
        for (int i = startIndex; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("backtrace:") || line.contains("memory near")) {
                break;
            }
            
            // 解析寄存器值
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
                    
                    // 根据寄存器名称设置对应的字段
                    switch (regName) {
                        case "x0":
                            registerDumpInfo.setX0(regValue);
                            break;
                        case "x1":
                            registerDumpInfo.setX1(regValue);
                            break;
                        case "x2":
                            registerDumpInfo.setX2(regValue);
                            break;
                        case "x3":
                            registerDumpInfo.setX3(regValue);
                            break;
                        case "x4":
                            registerDumpInfo.setX4(regValue);
                            break;
                        case "x5":
                            registerDumpInfo.setX5(regValue);
                            break;
                        case "x6":
                            registerDumpInfo.setX6(regValue);
                            break;
                        case "x7":
                            registerDumpInfo.setX7(regValue);
                            break;
                        case "x8":
                            registerDumpInfo.setX8(regValue);
                            break;
                        case "x9":
                            registerDumpInfo.setX9(regValue);
                            break;
                        case "x10":
                            registerDumpInfo.setX10(regValue);
                            break;
                        case "x11":
                            registerDumpInfo.setX11(regValue);
                            break;
                        case "x12":
                            registerDumpInfo.setX12(regValue);
                            break;
                        case "x13":
                            registerDumpInfo.setX13(regValue);
                            break;
                        case "x14":
                            registerDumpInfo.setX14(regValue);
                            break;
                        case "x15":
                            registerDumpInfo.setX15(regValue);
                            break;
                        case "x16":
                            registerDumpInfo.setX16(regValue);
                            break;
                        case "x17":
                            registerDumpInfo.setX17(regValue);
                            break;
                        case "x18":
                            registerDumpInfo.setX18(regValue);
                            break;
                        case "x19":
                            registerDumpInfo.setX19(regValue);
                            break;
                        case "x20":
                            registerDumpInfo.setX20(regValue);
                            break;
                        case "x21":
                            registerDumpInfo.setX21(regValue);
                            break;
                        case "x22":
                            registerDumpInfo.setX22(regValue);
                            break;
                        case "x23":
                            registerDumpInfo.setX23(regValue);
                            break;
                        case "x24":
                            registerDumpInfo.setX24(regValue);
                            break;
                        case "x25":
                            registerDumpInfo.setX25(regValue);
                            break;
                        case "x26":
                            registerDumpInfo.setX26(regValue);
                            break;
                        case "x27":
                            registerDumpInfo.setX27(regValue);
                            break;
                        case "x28":
                            registerDumpInfo.setX28(regValue);
                            break;
                        case "x29":
                            registerDumpInfo.setX29(regValue);
                            break;
                        case "lr":
                            registerDumpInfo.setX30(regValue);
                            break;
                        case "sp":
                            registerDumpInfo.setSp(regValue);
                            break;
                        case "pc":
                            registerDumpInfo.setPc(regValue);
                            break;
                    }
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }
            }
        }
        
        return registerDumpInfo;
    }
    
    private AArch64Tombstone.SpecialRegisterInfo parseSpecialRegisterInfo(List<String> lines, int startIndex) {
        // 解析特殊寄存器信息（lr, sp, pc, pst）
        // 示例: lr  0000007d0f7a7fb8  sp  0000007bdab4f9a0  pc  0000007d0f79d8cc  pst 0000000060001000
        
        String line = lines.get(startIndex).trim();
        Long lr = null, sp = null, pc = null, pst = null;
        boolean flag = false;
        // 分割行并解析各个寄存器值
        String[] parts = line.split("\s+");
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
                    case "lr":
                        lr = regValue;
                        flag = true;
                        break;
                    case "sp":
                        sp = regValue;
                        flag = true;
                        break;
                    case "pc":
                        pc = regValue;
                        flag = true;
                        break;
                    case "pst":
                        pst = regValue;
                        flag = true;
                        break;
                }
            } catch (NumberFormatException e) {
                // 忽略解析错误
                return null;
            }
        }
        if (!flag) {
            return null;
        }
        
        // 注意：SpecialRegisterInfo 类中的字段是 ESR, ELR, FAR, PSTATE
        // 但在实际的 tombstone 文件中，我们看到的是 lr, sp, pc, pst
        // 这里我们将 pst 映射到 PSTATE，其他字段设为 null
        return new AArch64Tombstone.SpecialRegisterInfo(lr, sp, pc, pst);
    }
    
    /**
     * 解析Fd信息
     */
    private List<AArch64Tombstone.FdInfo> parseFdInfo(List<String> lines, int startIndex) {
        List<AArch64Tombstone.FdInfo> fdInfos = new ArrayList<>();
        
        // 解析Fd信息，示例：
        // fd 0: /dev/null (unowned)
        // fd 9: anon_inode:[eventfd] (owned by unique_fd 0x7c65904a74)
        for (int i = startIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            
            // 如果遇到空行或其他部分的开始，则停止解析
            if (line.isEmpty() || 
                line.startsWith("Maps:") || 
                line.startsWith("memory map") ||
                line.startsWith("***")) {
                break;
            }
            
            // 解析Fd行
            if (line.startsWith("fd ")) {
                try {
                    // 格式: fd 0: /dev/null (unowned)
                    int colonIndex = line.indexOf(':');
                    if (colonIndex > 0) {
                        // 提取fd编号
                        String fdPart = line.substring(3, colonIndex).trim();
                        int fd = Integer.parseInt(fdPart);
                        
                        // 提取路径和fdsan信息
                        String remaining = line.substring(colonIndex + 1).trim();
                        int parenIndex = remaining.lastIndexOf(" (");
                        if (parenIndex > 0) {
                            String path = remaining.substring(0, parenIndex).trim();
                            String fdsanInfoStr = remaining.substring(parenIndex).trim();
                            
                            AArch64Tombstone.FdInfo.FdsanInfo fdsanInfo = AArch64Tombstone.FdInfo.parseFdsanInfo(fdsanInfoStr);
                            fdInfos.add(new AArch64Tombstone.FdInfo(fd, path, fdsanInfo));
                        } else {
                            // 没有fdsan信息的情况
                            fdInfos.add(new AArch64Tombstone.FdInfo(fd, remaining, null));
                        }
                    }
                } catch (Exception e) {
                    // 解析出错，跳过这一行
                    logger.warning("解析Fd信息失败: " + line + ", 错误: " + e.getMessage());
                }
            }
        }
        
        return fdInfos;
    }

}