package com.stability.martrix.util;

import com.stability.martrix.entity.AArch64Tombstone;

/**
 * Utility class for formatting tombstone data for AI analysis
 */
public class TombstoneFormatter {

    /**
     * Create a simplified AArch64Tombstone object for AI analysis
     * Includes only basic info, signal info, and top 5 stack traces
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

        // Copy stack dump info (top 5 frames only)
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
