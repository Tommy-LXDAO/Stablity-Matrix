package com.stability.martrix.util;

import com.stability.martrix.entity.AArch64Tombstone;

/**
 * Utility class for formatting tombstone data for AI analysis
 * Contains methods to convert tombstone objects to human-readable strings
 */
public class TombstoneFormatter {

    /**
     * Build tombstone information string for AI analysis
     * Includes top 20 stack traces and relevant crash information
     *
     * @param tombstone the tombstone data
     * @return formatted string representation
     */
    public static String buildTombstoneInfoForAI(AArch64Tombstone tombstone) {
        StringBuilder sb = new StringBuilder();

        // Basic information
        sb.append("=== Tombstone 崩溃信息 ===\n");
        sb.append("进程名: ").append(tombstone.getProcessName()).append("\n");
        sb.append("PID: ").append(tombstone.getPid()).append("\n");
        sb.append("TID: ").append(tombstone.getFirstTid()).append("\n");

        // Signal information
        if (tombstone.getSignalInfo() != null) {
            sb.append("\n=== 信号信息 ===\n");
            sb.append("信号编号: ").append(tombstone.getSignalInfo().getSigNumber()).append("\n");
            sb.append("信号信息: ").append(tombstone.getSignalInfo().getSigInformation()).append("\n");
            sb.append("故障信息: ").append(tombstone.getSignalInfo().getTroubleInformation()).append("\n");
            if (tombstone.getSignalInfo().getFaultAddress() != null) {
                sb.append("故障地址: 0x").append(Long.toHexString(tombstone.getSignalInfo().getFaultAddress())).append("\n");
            }
        }

        // Stack trace (top 20 frames)
        if (tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null) {
            sb.append("\n=== 堆栈信息 (Top 20) ===\n");
            var frames = tombstone.getStackDumpInfo().getStackFrames();
            int limit = Math.min(frames.size(), 20);

            sb.append(String.format("堆栈帧总数: %d (显示前 %d 帧)\n", frames.size(), limit));

            for (int i = 0; i < limit; i++) {
                var frame = frames.get(i);
                sb.append(String.format("#%02d ", i));

                if (frame.getAddress() != null) {
                    sb.append("pc 0x").append(Long.toHexString(frame.getAddress())).append("  ");
                }

                if (frame.getMapsInfo() != null) {
                    sb.append(frame.getMapsInfo());
                }

                if (frame.getSymbol() != null) {
                    sb.append(" (").append(frame.getSymbol()).append(")");
                }

                if (frame.getOffsetFromSymbolStart() != null) {
                    sb.append(" +").append(frame.getOffsetFromSymbolStart());
                }

                if (frame.getBuildId() != null) {
                    sb.append(" (BuildId: ").append(frame.getBuildId()).append(")");
                }

                sb.append("\n");
            }
        }

        // Register dump info (if available)
        if (tombstone.getRegisterDumpInfo() != null) {
            sb.append("\n=== 寄存器信息 ===\n");
            var regInfo = tombstone.getRegisterDumpInfo();

            // General purpose registers x0-x30
            sb.append(String.format("x0=%016x  x1=%016x  x2=%016x  x3=%016x\n",
                regInfo.getX0(), regInfo.getX1(), regInfo.getX2(), regInfo.getX3()));
            sb.append(String.format("x4=%016x  x5=%016x  x6=%016x  x7=%016x\n",
                regInfo.getX4(), regInfo.getX5(), regInfo.getX6(), regInfo.getX7()));
            sb.append(String.format("x8=%016x  x9=%016x  x10=%016x  x11=%016x\n",
                regInfo.getX8(), regInfo.getX9(), regInfo.getX10(), regInfo.getX11()));
            sb.append(String.format("x12=%016x  x13=%016x  x14=%016x  x15=%016x\n",
                regInfo.getX12(), regInfo.getX13(), regInfo.getX14(), regInfo.getX15()));
            sb.append(String.format("x16=%016x  x17=%016x  x18=%016x  x19=%016x\n",
                regInfo.getX16(), regInfo.getX17(), regInfo.getX18(), regInfo.getX19()));
            sb.append(String.format("x20=%016x  x21=%016x  x22=%016x  x23=%016x\n",
                regInfo.getX20(), regInfo.getX21(), regInfo.getX22(), regInfo.getX23()));
            sb.append(String.format("x24=%016x  x25=%016x  x26=%016x  x27=%016x\n",
                regInfo.getX24(), regInfo.getX25(), regInfo.getX26(), regInfo.getX27()));
            sb.append(String.format("x28=%016x  x29=%016x  x30=%016x\n",
                regInfo.getX28(), regInfo.getX29(), regInfo.getX30()));

            // Special registers
            sb.append(String.format("sp =%016x  pc =%016x\n",
                regInfo.getSp(), regInfo.getPc()));
        }

        // Special register info (lr, sp, pc, pst)
        if (tombstone.getSpecialRegisterInfo() != null) {
            sb.append("\n=== 特殊寄存器信息 ===\n");
            var specialReg = tombstone.getSpecialRegisterInfo();
            if (specialReg.getLr() != null) {
                sb.append(String.format("lr  =%016x  (Link Register)\n", specialReg.getLr()));
            }
            if (specialReg.getSp() != null) {
                sb.append(String.format("sp  =%016x  (Stack Pointer)\n", specialReg.getSp()));
            }
            if (specialReg.getPc() != null) {
                sb.append(String.format("pc  =%016x  (Program Counter)\n", specialReg.getPc()));
            }
            if (specialReg.getPst() != null) {
                sb.append(String.format("pst =%016x  (Processor State)\n", specialReg.getPst()));
            }
        }

        // File descriptor info (if available)
        if (tombstone.getFdInfo() != null && !tombstone.getFdInfo().isEmpty()) {
            sb.append("\n=== 文件描述符信息 ===\n");
            sb.append(String.format("打开的文件数量: %d\n", tombstone.getFdInfo().size()));
            for (var fdInfo : tombstone.getFdInfo()) {
                sb.append(String.format("  fd %d: %s", fdInfo.getFd(),
                    fdInfo.getPath() != null ? fdInfo.getPath() : "(null)"));
                if (fdInfo.getFdsanInfo() != null) {
                    sb.append(String.format(" (%s: 0x%x)",
                        fdInfo.getFdsanInfo().getOwnedType(),
                        fdInfo.getFdsanInfo().getOwner()));
                }
                sb.append("\n");
            }
        }

        // Memory maps info (if available)
        if (tombstone.getMapsInfoList() != null && !tombstone.getMapsInfoList().isEmpty()) {
            sb.append("\n=== 内存映射信息 ===\n");
            sb.append(String.format("内存映射数量: %d\n", tombstone.getMapsInfoList().size()));
            int mapLimit = Math.min(tombstone.getMapsInfoList().size(), 10);
            for (int i = 0; i < mapLimit; i++) {
                var mapInfo = tombstone.getMapsInfoList().get(i);
                sb.append(String.format("  [%d] 0x%016x - 0x%016x %s %s\n",
                    i,
                    mapInfo.getStart(),
                    mapInfo.getEnd(),
                    mapInfo.getPermission(),
                    mapInfo.getName() != null ? mapInfo.getName() : ""));
            }
            if (tombstone.getMapsInfoList().size() > 10) {
                sb.append(String.format("  ... (还有 %d 个映射)\n", tombstone.getMapsInfoList().size() - 10));
            }
        }

        return sb.toString();
    }

    /**
     * Build a summary of tombstone for quick analysis
     *
     * @param tombstone the tombstone data
     * @return brief summary string
     */
    public static String buildSummary(AArch64Tombstone tombstone) {
        return String.format(
            "进程: %s (PID: %d, TID: %d), 信号: %s (%s)",
            tombstone.getProcessName(),
            tombstone.getPid(),
            tombstone.getFirstTid(),
            tombstone.getSignalInfo() != null ? tombstone.getSignalInfo().getSigNumber() : "N/A",
            tombstone.getSignalInfo() != null ? tombstone.getSignalInfo().getSigInformation() : "N/A"
        );
    }

    /**
     * Build signal information string
     *
     * @param tombstone the tombstone data
     * @return formatted signal info
     */
    public static String buildSignalInfo(AArch64Tombstone tombstone) {
        if (tombstone.getSignalInfo() == null) {
            return "No signal information available";
        }

        var sigInfo = tombstone.getSignalInfo();
        StringBuilder sb = new StringBuilder();
        sb.append("信号编号: ").append(sigInfo.getSigNumber()).append("\n");
        sb.append("信号名称: ").append(sigInfo.getSigInformation()).append("\n");
        sb.append("故障信息: ").append(sigInfo.getTroubleInformation()).append("\n");

        if (sigInfo.getFaultAddress() != null) {
            sb.append("故障地址: 0x").append(Long.toHexString(sigInfo.getFaultAddress())).append("\n");
        }

        if (sigInfo.getFromPid() != null) {
            sb.append("来自PID: ").append(sigInfo.getFromPid()).append("\n");
        }

        if (sigInfo.getFromUid() != null) {
            sb.append("来自UID: ").append(sigInfo.getFromUid()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Create a simplified AArch64Tombstone object for AI analysis
     * Includes only basic info, signal info, and top 10 stack traces
     * Excludes file descriptors, memory maps, and register dumps
     *
     * @param tombstone the original tombstone data
     * @return simplified AArch64Tombstone object
     */
    public static AArch64Tombstone createSimplifiedTombstone(AArch64Tombstone tombstone) {
        AArch64Tombstone simplified = new AArch64Tombstone();

        // Copy basic information from parent class
        simplified.setPid(tombstone.getPid());
        simplified.setFirstTid(tombstone.getFirstTid());
        simplified.setProcessName(tombstone.getProcessName());
        simplified.setVersion(tombstone.getVersion());

        // Copy CPU architecture
        simplified.setCpuArchitecture(tombstone.getCpuArchitecture());

        // Copy signal info
        if (tombstone.getSignalInfo() != null) {
            AArch64Tombstone.SignalInfo signalInfo = new AArch64Tombstone.SignalInfo(
                tombstone.getSignalInfo().getSigNumber(),
                tombstone.getSignalInfo().getSigInformation(),
                tombstone.getSignalInfo().getTroubleInformation(),
                tombstone.getSignalInfo().getFaultAddress(),
                tombstone.getSignalInfo().getFromPid(),
                tombstone.getSignalInfo().getFromUid()
            );
            simplified.setSignalInfo(signalInfo);
        }

        // Copy stack dump info (top 10 frames only)
        if (tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null) {
            var originalFrames = tombstone.getStackDumpInfo().getStackFrames();
            int limit = Math.min(originalFrames.size(), 5);

            // Create simplified stack dump info
            AArch64Tombstone.StackDumpInfo stackDumpInfo = new AArch64Tombstone.StackDumpInfo();
            stackDumpInfo.setStackFrames(new java.util.ArrayList<>());

            // Copy only top 5 frames
            for (int i = 0; i < limit; i++) {
                var originalFrame = originalFrames.get(i);
                var simplifiedFrame = new AArch64Tombstone.StackDumpInfo.StackFrame(
                    originalFrame.getOffsetFromSymbolStart(),
                    originalFrame.getSymbol(),
                    originalFrame.getMapsInfo(),
                    originalFrame.getAddressType(),
                    originalFrame.getAddress(),
                    originalFrame.getIndex(),
                    originalFrame.getBuildId()
                );
                stackDumpInfo.getStackFrames().add(simplifiedFrame);
            }

            simplified.setStackDumpInfo(stackDumpInfo);
        }

        // Note: Intentionally NOT copying fdInfo, mapsInfoList, registerDumpInfo, specialRegisterInfo
        // to keep the tombstone object lightweight for AI analysis

        return simplified;
    }
}
