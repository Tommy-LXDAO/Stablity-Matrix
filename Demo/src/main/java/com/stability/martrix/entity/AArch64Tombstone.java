package com.stability.martrix.entity;

import com.stability.martrix.entity.register.AArch64RegisterDumpInfo;
import com.stability.martrix.enums.CPUArchitecture;
import lombok.Data;
import reactor.core.publisher.Signal;

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
        public static class StackFrame{
            private int index;
            private Long address;
            private AddressType addressType;
            private String mapsInfo;
            private String symbol;
            private Long offsetFromSymbolStart;
            public static enum AddressType {
                ABSOLUTE,
                OFFSET
            }

            public StackFrame(Long offsetFromSymbolStart, String symbol, String mapsInfo, AddressType addressType, Long address, int index) {
                this.offsetFromSymbolStart = offsetFromSymbolStart;
                this.symbol = symbol;
                this.mapsInfo = mapsInfo;
                this.addressType = addressType;
                this.address = address;
                this.index = index;
            }
        }
    }

    @Data
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
    public static class FdInfo {
        private int fd;
        private String path;

        public FdInfo(int fd, String path) {
            this.fd = fd;
            this.path = path;
        }
    }

    @Data
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
    public static class SpecialRegisterInfo {
        private Long ESR;
        private Long ELR;
        private Long FAR;
        private Long PSTATE;
        public SpecialRegisterInfo(Long ESR, Long ELR, Long FAR, Long PSTATE) {
            this.ESR = ESR;
            this.ELR = ELR;
            this.FAR = FAR;
            this.PSTATE = PSTATE;
        }
    }

}
