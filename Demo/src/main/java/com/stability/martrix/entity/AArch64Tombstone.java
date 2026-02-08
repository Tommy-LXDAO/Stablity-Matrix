package com.stability.martrix.entity;

import com.stability.martrix.entity.register.AArch64RegisterDumpInfo;
import com.stability.martrix.enums.CPUArchitecture;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 故障现场信息
@Data
public class AArch64Tombstone extends TroubleEntity {
    private CPUArchitecture cpuArchitecture;
    private StackDumpInfo stackDumpInfo; // 栈信息
    private SignalInfo signalInfo; // 信号信息
    private List<FdInfo> fdInfo; // fd 信息
    private List<MapsInfo> mapsInfoList; // maps 信息
    private AArch64RegisterDumpInfo registerDumpInfo; // dump信息
    private SpecialRegisterInfo specialRegisterInfo; // 特殊寄存器信息

    @Data
    public static class StackDumpInfo{
        private List<StackFrame> stackFrames;
        @Data
        @NoArgsConstructor
        public static class StackFrame{
            private int index;
            private Long address;
            private AddressType addressType;
            private String mapsInfo;
            private String symbol;
            private Long offsetFromSymbolStart;
            private String buildId;
            public static enum AddressType {
                ABSOLUTE,
                OFFSET
            }

            public StackFrame(Long offsetFromSymbolStart, String symbol, String mapsInfo, AddressType addressType, Long address, int index, String buildId) {
                this.offsetFromSymbolStart = offsetFromSymbolStart;
                this.symbol = symbol;
                this.mapsInfo = mapsInfo;
                this.addressType = addressType;
                this.address = address;
                this.index = index;
                this.buildId = buildId;
            }
        }
    }

    @Data
    @NoArgsConstructor
    public static class SignalInfo {
        private int sigNumber;
        private String sigInformation;
        private String troubleInformation;
        private Long faultAddress;
        private Integer fromPid;
        private Integer fromUid;
        public SignalInfo(int sigNumber, String sigInformation, String troubleInformation, Long faultAddress, Integer fromPid, Integer fromUid) {
            this.sigNumber = sigNumber;
            this.sigInformation = sigInformation;
            this.troubleInformation = troubleInformation;
            this.faultAddress = faultAddress;
            this.fromPid = fromPid;
            this.fromUid = fromUid;
        }

        public SignalInfo(int sigNumber) {
            this.sigNumber = sigNumber;
        }
    }

    @Data
    @NoArgsConstructor
    public static class FdInfo {
        private int fd;
        private String path;
        private FdsanInfo fdsanInfo;

        public FdInfo(int fd, String path, FdsanInfo fdsanInfo) {
            this.fd = fd;
            this.path = path;
            this.fdsanInfo = fdsanInfo;
        }

        @Data
        @NoArgsConstructor
        public static class FdsanInfo{
            private String ownedType;
            private long owner;
            
            public FdsanInfo(String ownedType, long owner) {
                this.ownedType = ownedType;
                this.owner = owner;
            }
        }

        public static FdsanInfo parseFdsanInfo(String fdsanInfo) {
            if (fdsanInfo == null || fdsanInfo.isEmpty()) {
                return null;
            }
            
            // 解析格式如 "(owned by unique_fd 0x7c65904a74)" 或 "(unowned)"
            fdsanInfo = fdsanInfo.trim();
            if (fdsanInfo.equals("(unowned)")) {
                return null; // 未拥有的文件描述符
            }
            
            if (fdsanInfo.startsWith("(owned by ") && fdsanInfo.endsWith(")")) {
                // 提取中间部分: "unique_fd 0x7c65904a74"
                String content = fdsanInfo.substring(10, fdsanInfo.length() - 1);
                String[] parts = content.split(" ");
                if (parts.length == 2) {
                    String ownedType = parts[0]; // 如 "unique_fd"
                    long owner = 0;
                    try {
                        // 解析十六进制地址，如 "0x7c65904a74"
                        String hexStr = parts[1];
                        if (hexStr.startsWith("0x")) {
                            owner = Long.parseLong(hexStr.substring(2), 16);
                        } else {
                            owner = Long.parseLong(hexStr, 16);
                        }
                    } catch (NumberFormatException e) {
                        // 如果解析失败，使用默认值0
                    }
                    return new FdsanInfo(ownedType, owner);
                }
            }
            
            return null;
        }
    }

    @Data
    @NoArgsConstructor
    public static class MapsInfo {
        private Long start;
        private Long end;
        private String permission;
        private String name;
        public MapsInfo(Long start, Long end, String permission, String name) {
            this.start = start;
            this.end = end;
            this.permission = permission;
            this.name = name;
        }
    }

    @Data
    @NoArgsConstructor
    public static class SpecialRegisterInfo {
        private Long lr;
        private Long sp;
        private Long pc;
        private Long pst;
        
        public SpecialRegisterInfo(Long lr, Long sp, Long pc, Long pst) {
            this.lr = lr;
            this.sp = sp;
            this.pc = pc;
            this.pst = pst;
        }
    }

}
