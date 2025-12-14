package com.stability.martrix.service;

import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;

public class AndroidAArch64FileServiceTest {
    public static void main(String[] args) {
        AndroidAArch64FileService service = new AndroidAArch64FileService();
        TroubleEntity entity = service.parseFile("/Users/lyw/IdeaProjects/Stablity-Matrix/Demo/src/main/resources/tombstone_00");
        
        if (entity instanceof AArch64Tombstone tombstone) {
            System.out.println("进程名: " + tombstone.getProcessName());
            System.out.println("PID: " + tombstone.getPid());
            System.out.println("TID: " + tombstone.getFirstTid());
            
            if (tombstone.getSignalInfo() != null) {
                System.out.println("信号编号: " + tombstone.getSignalInfo().getSigNumber());
                System.out.println("信号信息: " + tombstone.getSignalInfo().getSigInformation());
                System.out.println("故障信息: " + tombstone.getSignalInfo().getTroubleInformation());
                System.out.println("故障地址: " + tombstone.getSignalInfo().getFaultAddress());
            }
            
            if (tombstone.getStackDumpInfo() != null && tombstone.getStackDumpInfo().getStackFrames() != null) {
                System.out.println("堆栈帧数量: " + tombstone.getStackDumpInfo().getStackFrames().size());
                tombstone.getStackDumpInfo().getStackFrames().forEach(frame -> {
                    System.out.println("  #" + frame.getIndex() + " 地址: 0x" + Long.toHexString(frame.getAddress() != null ? frame.getAddress() : 0) 
                        + " 映射: " + frame.getMapsInfo() + " 符号: " + frame.getSymbol());
                });
            }
            
            if (tombstone.getRegisterDumpInfo() != null) {
                System.out.println("寄存器信息:");
                System.out.println("  X0: 0x" + Long.toHexString(tombstone.getRegisterDumpInfo().getX0()));
                System.out.println("  X1: 0x" + Long.toHexString(tombstone.getRegisterDumpInfo().getX1()));
                System.out.println("  PC: 0x" + Long.toHexString(tombstone.getRegisterDumpInfo().getPc()));
                System.out.println("  SP: 0x" + Long.toHexString(tombstone.getRegisterDumpInfo().getSp()));
            }
        }
    }
}