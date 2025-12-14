package com.stability.martrix.entity;

import com.stability.martrix.entity.register.AArch64RegisterDumpInfo;
import com.stability.martrix.entity.register.RegisterDumpInfo;
import com.stability.martrix.enums.CPUArchitecture;
import lombok.Data;

import java.util.List;

// 故障现场信息
@Data
public class Tombstone extends TroubleEntity {
    private String stackTopRawInformation;
    private int sigNumber;
    private String sigInformation;
    private String troubleInformation;
    private CPUArchitecture cpuArchitecture;
    private StackDumpInfo stackDumpInfo; // 栈信息
    private SignalInfo signalInfo; // 信号信息
    private List<FdInfo> fdInfo; // fd 信息
    private List<MapsInfo> mapsInfoList; // maps 信息
    private RegisterDumpInfo registerDumpInfo; // dump信息

    @Data
    static class StackDumpInfo{
        private List<StackFrame> stackFrames;
        @Data
        static class StackFrame{
            private int index;
            private Long address;
            private AddressType addressType;
            private String mapsInfo;
            private String symbol;
            private Long offsetFromSymbolStart;
            static enum AddressType {
                ABSOLUTE,
                OFFSET
            }
        }
    }

    @Data
    static class SignalInfo {
        private int sigNumber;
        private String sigInformation;
        private String troubleInformation;
        private Long FAR;
        private Integer fromPid;
        private Integer fromUid;
    }

    static class FdInfo {
        private int fd;
        private String path;
    }

    static class MapsInfo {
        private Long start;
        private Long end;
        private String permission;
        private String name;
    }

}
