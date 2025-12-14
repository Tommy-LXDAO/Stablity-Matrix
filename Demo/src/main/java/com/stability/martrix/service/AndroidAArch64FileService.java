package com.stability.martrix.service;

import com.stability.martrix.annotation.AndroidAArch64Demo;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.entity.register.AArch64RegisterDumpInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@AndroidAArch64Demo
public class AndroidAArch64FileService implements FileService {
    private static final Logger logger = Logger.getLogger(AndroidAArch64FileService.class.getName());

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
        // 1. 打开文件，读取文件内容
        try {
            List<String> strings = Files.readAllLines(Paths.get(filePath));
            for (int i = 0; i < strings.size(); i++) {
                String line = strings.get(i);
                // next TODO:通过每一行的信息解析必要的日志信息
                if (line.contains("Cmdline:")) {
                    String[] parts = line.split(": ", 2);
                    if (parts.length >= 2) {
                        tombstone.setProcessName(parts[1].trim());
                    }
                } else if (line.startsWith("pid:")) {
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
                } else if (line.startsWith("signal ")) {
                    tombstone.setSignalInfo(parseSignalInfo(line));
                } else if (line.startsWith("backtrace:")) {
                    // 解析堆栈回溯信息
                    tombstone.setStackDumpInfo(parseBacktraceInfo(strings, i));
                } else if (line.contains("Registers:")) {
                    // 解析寄存器信息
                    tombstone.setRegisterDumpInfo(parseRegisterInfo(strings, i));
                } else if (line.contains("Special:")) {
                    // 解析特殊寄存器信息
                    tombstone.setSpecialRegisterInfo(parseSpecialRegisterInfo(strings, i));
                } else if (line.contains("Fd:")) {
                    // 解析文件描述符信息

                } else if (line.contains("Maps:")) {
                    // 解析内存映射信息

                }
            }
        } catch (IOException e) {
            logger.info("文件"+filePath+"打开失败"+e.getMessage());
        }
        // 2. 通过工具直接解析标准格式的文件，并得到结果，循环遍历每一行

        return tombstone;
    }

    private AArch64Tombstone.SignalInfo parseSignalInfo(String line) {
        String[] s = line.split(" ");
        if (s.length < 8) {
            throw new RuntimeException("解析信号信息失败，字符串长度"+s.length+" 字符串信息:"+line);
        }
        // 解析 signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x7c5072d048
        AArch64Tombstone.SignalInfo signalInfo = new AArch64Tombstone.SignalInfo(Integer.parseInt(s[1]));
        signalInfo.setSigInformation(s[3].replaceAll("[(),]", ""));
        signalInfo.setTroubleInformation(s[5].replaceAll("[(),]", ""));
        
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
                if (parts.length >= 5) {
                    mapsInfo = parts[3]; // 共享库路径
                    symbol = parts[4];   // 符号信息（函数名等）
                    
                    // 清理符号信息，移除括号
                    if (symbol.startsWith("(") && symbol.endsWith(")")) {
                        symbol = symbol.substring(1, symbol.length() - 1);
                    }
                } else if (parts.length >= 4) {
                    mapsInfo = parts[3];
                }
                
                AArch64Tombstone.StackDumpInfo.StackFrame stackFrame = new AArch64Tombstone.StackDumpInfo.StackFrame(
                    null, // offsetFromSymbolStart
                    symbol, // symbol
                    mapsInfo, // mapsInfo
                    AArch64Tombstone.StackDumpInfo.StackFrame.AddressType.ABSOLUTE, // addressType
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
        for (int i = startIndex + 1; i < lines.size(); i++) {
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
        // 解析特殊寄存器信息（ESR, ELR, FAR, PSTATE等）
        Long ESR = null;
        Long ELR = null;
        Long FAR = null;
        Long PSTATE = null;
        
        // 在示例文件中没有看到Special部分的详细内容，暂时返回null
        // 如果有Special部分，可以在这里添加解析逻辑
        
        if (ESR != null || ELR != null || FAR != null || PSTATE != null) {
            return new AArch64Tombstone.SpecialRegisterInfo(ESR, ELR, FAR, PSTATE);
        }
        return null;
    }

}
