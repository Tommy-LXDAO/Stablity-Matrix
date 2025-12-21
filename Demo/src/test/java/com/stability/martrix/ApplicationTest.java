package com.stability.martrix;

import com.stability.martrix.config.AppConfig;
import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.service.AndroidAArch64FileService;
import com.stability.martrix.service.ResourceReaderService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

public class ApplicationTest {
    public static void main(String[] args) {
        // 创建Spring应用上下文
        ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        
        // 获取服务bean
        AndroidAArch64FileService service = context.getBean(AndroidAArch64FileService.class);
        ResourceReaderService resourceReaderService = context.getBean(ResourceReaderService.class);
        
        // 使用资源路径解析文件
        System.out.println("=== 使用资源路径解析 ===");
        TroubleEntity entity1 = service.parseResourceFile("tombstone_00");
        printTombstoneInfo(entity1);
        
        // 使用List<String>参数解析文件
        System.out.println("\n=== 使用List<String>参数解析 ===");
        List<String> lines = resourceReaderService.readLinesFromResource("tombstone_00");
        TroubleEntity entity2 = service.parseFile(lines);
        printTombstoneInfo(entity2);
    }
    
    private static void printTombstoneInfo(TroubleEntity entity) {
        if (entity instanceof AArch64Tombstone) {
            AArch64Tombstone tombstone = (AArch64Tombstone) entity;
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