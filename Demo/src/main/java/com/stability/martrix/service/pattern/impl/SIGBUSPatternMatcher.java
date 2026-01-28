package com.stability.martrix.service.pattern.impl;

import com.stability.martrix.dto.PatternMatchResult;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.enums.SignalType;
import com.stability.martrix.exception.InvalidTombstoneException;
import com.stability.martrix.service.pattern.SignalPatternMatcher;
import org.springframework.stereotype.Service;

/**
 * Pattern matcher for SIGBUS (signal 7)
 * Analyzes bus errors: memory alignment issues, accessing non-existent physical memory
 */
@Service
public class SIGBUSPatternMatcher implements SignalPatternMatcher {

    @Override
    public PatternMatchResult match(AArch64Tombstone tombstone) {
        // Check for null signal info
        if (tombstone.getSignalInfo() == null) {
            throw new InvalidTombstoneException("No signal information available for SIGBUS analysis");
        }

        // Validate it's a true SIGBUS
        if (tombstone.getSignalInfo().getSigNumber() != SignalType.SIGBUS.getSignalNumber()) {
            throw new InvalidTombstoneException("Invalid signal number for SIGBUS analysis");
        }

        // Mode 1: Check for Memory Alignment Error (most common on AArch64)
        PatternMatchResult alignmentResult = checkMemoryAlignmentError(tombstone);
        if (alignmentResult != null) {
            return alignmentResult;
        }

        // Mode 2: Check for Accessing Non-existent Physical Memory
        PatternMatchResult nonExistentMemoryResult = checkNonExistentMemory(tombstone);
        if (nonExistentMemoryResult != null) {
            return nonExistentMemoryResult;
        }

        // Mode 3: Check for Device Memory Access Error
        PatternMatchResult deviceMemoryResult = checkDeviceMemoryAccess(tombstone);
        if (deviceMemoryResult != null) return deviceMemoryResult;

        // No specific pattern matched
        return null;
    }

    /**
     * Check for memory alignment error
     * AArch64 requires strict alignment for: ldr/str (natural alignment), ldp/stp (pair access)
     * Common causes: accessing struct members with wrong alignment, packed struct issues
     */
    private PatternMatchResult checkMemoryAlignmentError(AArch64Tombstone tombstone) {
        Long faultAddress = tombstone.getSignalInfo().getFaultAddress();
        String troubleInfo = tombstone.getSignalInfo().getTroubleInformation();

        if (faultAddress == null) {
            return null;
        }

        // Check if trouble info indicates alignment error
        boolean isAlignmentError = troubleInfo != null &&
            (troubleInfo.contains("alignment") ||
             troubleInfo.contains("ALIGN") ||
             troubleInfo.contains("BUS_ADRALN") ||
             troubleInfo.contains("adraln"));

        // On AArch64, alignment faults are common with:
        // - Odd addresses for 4-byte aligned access (fault address % 4 != 0)
        // - Addresses not divisible by 8 for 8-byte aligned access (fault address % 8 != 0)
        // - Addresses not divisible by 16 for 16-byte aligned access (fault address % 16 != 0)
        boolean hasAlignmentIssue = (faultAddress & 0x3L) != 0 ||  // Not 4-byte aligned
                                    (faultAddress & 0x7L) != 0;   // Not 8-byte aligned

        if (isAlignmentError || hasAlignmentIssue) {
            String callerInfo = getCrashCallerInfo(tombstone);
            String alignmentType = getAlignmentType(faultAddress);

            return PatternMatchResult.builder()
                .confidence(isAlignmentError ? 0.98 : 0.85)
                .result("检测到内存对齐错误: 故障地址 0x" + Long.toHexString(faultAddress) +
                    " 不满足 " + alignmentType + " 对齐要求。" + callerInfo +
                    " 请检查数据结构定义，特别是结构体成员的对齐方式，以及 packed 属性的使用。")
                .aiPrompt("检测到内存对齐错误，故障地址 0x" + Long.toHexString(faultAddress) +
                    "，不满足 " + alignmentType + " 对齐要求。在AArch64架构上，请提醒用户：" +
                    "1) 检查结构体是否有正确的对齐属性；2) 检查是否使用了 __attribute__((packed)) 导致非自然对齐；" +
                    "3) 检查指针类型转换是否导致对齐问题；4) 建议使用 -Wcast-align 编译器选项检测潜在问题。" +
                    callerInfo)
                .directConclusion(true)
                .build();
        }

        return null;
    }

    /**
     * Check for accessing non-existent physical memory
     * This can happen when: page has been swapped out, memory-mapped file deleted, etc.
     */
    private PatternMatchResult checkNonExistentMemory(AArch64Tombstone tombstone) {
        Long faultAddress = tombstone.getSignalInfo().getFaultAddress();
        String troubleInfo = tombstone.getSignalInfo().getTroubleInformation();

        if (faultAddress == null) {
            return null;
        }

        // Check for non-existent memory patterns
        boolean isNonExistentMemory = troubleInfo != null &&
            (troubleInfo.contains("non-existent") ||
             troubleInfo.contains("BUS_ADRERR") ||
             troubleInfo.contains("adrerr") ||
             troubleInfo.contains("physical") ||
             troubleInfo.contains("pfn") ||
             troubleInfo.contains("pageframe"));

        // Check if fault address is in a memory-mapped region that might be deleted
        boolean inMappedRegion = isInMappedFileRegion(tombstone);

        if (isNonExistentMemory || inMappedRegion) {
            String callerInfo = getCrashCallerInfo(tombstone);
            String memoryType = inMappedRegion ? "内存映射文件区域" : "物理内存";

            return PatternMatchResult.builder()
                .confidence(isNonExistentMemory ? 0.95 : 0.70)
                .result("检测到访问不存在的" + memoryType + ": 故障地址 0x" + Long.toHexString(faultAddress) +
                    "。" + callerInfo +
                    " 这可能是因为映射的文件已被删除、内存被交换出或访问了已释放的内存映射。")
                .aiPrompt("检测到访问不存在的内存，故障地址 0x" + Long.toHexString(faultAddress) +
                    "。请提醒用户：1) 检查内存映射文件是否在访问前被删除；2) 检查mmap后是否正确处理了信号；" +
                    "3) 检查内存映射的生命周期管理；4) 检查是否有use-after-mmap问题。" +
                    callerInfo)
                .directConclusion(true)
                .build();
        }

        return null;
    }

    /**
     * Check for device memory access error
     * This can happen when accessing /dev/mem or /dev/kmem with wrong alignment
     */
    private PatternMatchResult checkDeviceMemoryAccess(AArch64Tombstone tombstone) {
        String troubleInfo = tombstone.getSignalInfo().getTroubleInformation();

        // Check for device memory access patterns
        boolean isDeviceMemoryError = troubleInfo != null &&
            (troubleInfo.contains("device") ||
             troubleInfo.contains("/dev/") ||
             troubleInfo.contains("kmem") ||
             troubleInfo.contains("iomem"));

        if (isDeviceMemoryError) {
            String callerInfo = getCrashCallerInfo(tombstone);

            return PatternMatchResult.builder()
                .confidence(0.90)
                .result("检测到设备内存访问错误: " + troubleInfo + "。" + callerInfo +
                    " 请检查设备内存访问的地址和对齐方式。")
                .aiPrompt("检测到设备内存访问错误，" + troubleInfo +
                    "。请提醒用户：1) 检查设备内存访问的地址是否有效；2) 检查访问是否满足设备要求的对齐；" +
                    "3) 检查是否有权限访问该设备内存；4) 避免直接访问用户空间不允许的物理地址。" +
                    callerInfo)
                .directConclusion(true)
                .build();
        }

        return null;
    }

    /**
     * Determine the alignment type based on fault address
     */
    private String getAlignmentType(long faultAddress) {
        // Check if it fails 4-byte alignment
        if ((faultAddress & 0x3L) != 0) {
            return "4字节";
        }
        // Check if it fails 8-byte alignment
        if ((faultAddress & 0x7L) != 0) {
            return "8字节";
        }
        // Check if it fails 16-byte alignment
        if ((faultAddress & 0xFL) != 0) {
            return "16字节";
        }
        return "自然";
    }

    /**
     * Check if fault address is in a memory-mapped file region
     */
    private boolean isInMappedFileRegion(AArch64Tombstone tombstone) {
        if (tombstone.getMapsInfoList() == null || tombstone.getMapsInfoList().isEmpty()) {
            return false;
        }

        Long faultAddress = tombstone.getSignalInfo().getFaultAddress();
        if (faultAddress == null) {
            return false;
        }

        for (AArch64Tombstone.MapsInfo mapsInfo : tombstone.getMapsInfoList()) {
            if (mapsInfo.getStart() != null && mapsInfo.getEnd() != null) {
                if (faultAddress >= mapsInfo.getStart() &&
                    faultAddress < mapsInfo.getEnd()) {
                    // Check if it's a file mapping (contains /)
                    if (mapsInfo.getName() != null &&
                        mapsInfo.getName().contains("/")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Get crash caller information from stack trace
     */
    private String getCrashCallerInfo(AArch64Tombstone tombstone) {
        if (tombstone.getStackDumpInfo() == null ||
            tombstone.getStackDumpInfo().getStackFrames() == null ||
            tombstone.getStackDumpInfo().getStackFrames().isEmpty()) {
            return "";
        }

        // Get the first non-system library frame
        for (int i = 0; i < tombstone.getStackDumpInfo().getStackFrames().size(); i++) {
            AArch64Tombstone.StackDumpInfo.StackFrame frame =
                tombstone.getStackDumpInfo().getStackFrames().get(i);
            if (frame.getMapsInfo() != null &&
                !frame.getMapsInfo().contains("libc") &&
                !frame.getMapsInfo().contains("musl") &&
                !frame.getMapsInfo().contains("bionic") &&
                frame.getSymbol() != null) {
                return " 调用者: " + frame.getSymbol() + " (" + frame.getMapsInfo() + ")";
            }
        }

        return "";
    }

    @Override
    public int getSupportedSignalNumber() {
        return SignalType.SIGBUS.getSignalNumber();
    }
}
