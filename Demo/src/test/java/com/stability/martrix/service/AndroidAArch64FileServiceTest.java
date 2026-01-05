package com.stability.martrix.service;

import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.service.impl.SpringResourceReaderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AndroidAArch64FileServiceTest {
    public static void main(String[] args) {
        // 测试文件系统路径读取
//        testFileSystemParsing();
        
        // 测试资源路径读取
//        testResourceParsing();
        
        // 测试直接使用List<String>参数
        testListParsing();
    }
    
    private static void testFileSystemParsing() {
        System.out.println("=== 测试文件系统路径读取 ===");
        AndroidAArch64FileService service = new AndroidAArch64FileService();
        TroubleEntity entity = service.parseFile("/Users/lyw/IdeaProjects/Stablity-Matrix/Demo/src/main/resources/tombstone_00");
        
        printTombstoneInfo(entity);
    }
    
    private static void testResourceParsing() {
        System.out.println("=== 测试资源路径读取 ===");
        ResourceReaderService resourceReaderService = new SpringResourceReaderServiceImpl();
        AndroidAArch64FileService service = new AndroidAArch64FileService(resourceReaderService);
        TroubleEntity entity = service.parseResourceFile("tombstone_00");
        
        printTombstoneInfo(entity);
    }
    
    private static void testListParsing() {
        System.out.println("=== 测试直接使用List<String>参数 ===");
        try {
            // 从文件读取内容到List<String>
            List<String> lines = Files.readAllLines(
                Paths.get("/Users/lyw/IdeaProjects/Stablity-Matrix/Demo/src/main/resources/tombstone_00"));
            
            // 使用新的接口方法解析
            AndroidAArch64FileService service = new AndroidAArch64FileService();
            TroubleEntity entity = service.parseFile(lines);
            
            // 校验解析结果
            validateTombstoneInfo(entity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void validateTombstoneInfo(TroubleEntity entity) {
        if (entity instanceof AArch64Tombstone) {
            AArch64Tombstone tombstone = (AArch64Tombstone) entity;
            
            // 校验基本信息
            assert tombstone.getProcessName().equals("com.apkpure.aegon") : "进程名不匹配";
            assert tombstone.getPid() == 16369 : "PID不匹配";
            assert tombstone.getFirstTid() == 16370; // 注意：tid是16370，不是16369
            
            // 校验信号信息
            assert tombstone.getSignalInfo() != null : "信号信息为空";
            assert tombstone.getSignalInfo().getSigNumber() == 11 : "信号编号不匹配";
            assert "SIGSEGV".equals(tombstone.getSignalInfo().getSigInformation()) : "信号信息不匹配";
            assert "SEGV_MAPERR".equals(tombstone.getSignalInfo().getTroubleInformation()) : "故障信息不匹配";
            assert tombstone.getSignalInfo().getFaultAddress() != null : "故障地址为空";
            assert tombstone.getSignalInfo().getFaultAddress() == 0x7c5072d048L : "故障地址不匹配";
            
            // 校验堆栈信息
            assert tombstone.getStackDumpInfo() != null : "堆栈信息为空";
            assert tombstone.getStackDumpInfo().getStackFrames() != null : "堆栈帧为空";
            assert tombstone.getStackDumpInfo().getStackFrames().size() >= 4 : "堆栈帧数量不足";
            
            // 校验具体的堆栈帧信息
            List<AArch64Tombstone.StackDumpInfo.StackFrame> frames = tombstone.getStackDumpInfo().getStackFrames();
            assert frames.get(0).getIndex() == 0 : "第一个堆栈帧索引不正确";
            assert frames.get(0).getAddress() == 0x5a8ccL : "第一个堆栈帧地址不正确";
            assert "/system/lib64/libbinder.so".equals(frames.get(0).getMapsInfo()) : "第一个堆栈帧映射信息不正确";
            assert frames.get(0).getSymbol() != null : "第一个堆栈帧符号信息为空";
            assert frames.get(0).getSymbol().contains("android::Parcel::ipcSetDataReference") : "第一个堆栈帧符号信息不正确";
            
            assert frames.get(1).getIndex() == 1 : "第二个堆栈帧索引不正确";
            assert frames.get(1).getAddress() == 0x64fb4L : "第二个堆栈帧地址不正确";
            assert "/system/lib64/libbinder.so".equals(frames.get(1).getMapsInfo()) : "第二个堆栈帧映射信息不正确";
            assert frames.get(1).getSymbol() != null : "第二个堆栈帧符号信息为空";
            assert frames.get(1).getSymbol().contains("android::IPCThreadState::waitForResponse") : "第二个堆栈帧符号信息不正确";
            
            assert frames.get(2).getIndex() == 2 : "第三个堆栈帧索引不正确";
            assert frames.get(2).getAddress() == 0x597acL : "第三个堆栈帧地址不正确";
            assert "/system/lib64/libbinder.so".equals(frames.get(2).getMapsInfo()) : "第三个堆栈帧映射信息不正确";
            assert frames.get(2).getSymbol() != null : "第三个堆栈帧符号信息为空";
            assert frames.get(2).getSymbol().contains("android::IPCThreadState::transact") : "第三个堆栈帧符号信息不正确";
            
            assert frames.get(3).getIndex() == 3 : "第四个堆栈帧索引不正确";
            assert frames.get(3).getAddress() == 0x5cf80L : "第四个堆栈帧地址不正确";
            assert "/system/lib64/libbinder.so".equals(frames.get(3).getMapsInfo()) : "第四个堆栈帧映射信息不正确";
            
            // 校验寄存器信息
            assert tombstone.getRegisterDumpInfo() != null : "寄存器信息为空";
            // 校验所有通用寄存器
            assert tombstone.getRegisterDumpInfo().getX0() == 0 : "X0寄存器值不匹配";
            assert tombstone.getRegisterDumpInfo().getX1() == 0x7c5072d028L : "X1寄存器值不匹配";
            assert tombstone.getRegisterDumpInfo().getX2() == 0x20L : "X2寄存器值不匹配";
            assert tombstone.getRegisterDumpInfo().getX3() == 0x7c5072d048L : "X3寄存器值不匹配";
            assert tombstone.getRegisterDumpInfo().getX4() == 0x1L : "X4寄存器值不匹配";
            assert tombstone.getRegisterDumpInfo().getX5() == 0x0L : "X5寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX6() == 0x0L : "X6寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX7() == 0x0L : "X7寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX8() == 0x0L : "X8寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX9() == 0x73682a85L : "X9寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX10() == 0x66642a85L : "X10寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX11() == 0x73622a85L : "X11寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX12() == 0x7c5072d028L : "X12寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX13() == 0x7c5072d048L : "X13寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX14() == 0x7bdab4fcc0L : "X14寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX15() == 0x0L : "X15寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX16() == 0x7d0f7faf50L : "X16寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX17() == 0x7d09b42400L : "X17寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX18() == 0x7bda2d0000L : "X18寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX19() == 0x7c50ab0180L : "X19寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX20() == 0x1L : "X20寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX21() == 0x7c5072d048L : "X21寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX22() == 0x7c5072d028L : "X22寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX23() == 0x7d0f7a87fcL : "X23寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX24() == 0x20L : "X24寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX25() == 0x7bdab51000L : "X25寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX26() == 0x7c50ab0188L : "X26寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX27() == 0x7c50ab01a8L : "X27寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX28() == 0x7d0f7fc000L : "X28寄存器값不匹配";
            assert tombstone.getRegisterDumpInfo().getX29() == 0x7bdab4f9d0L : "X29寄存器값不匹配";
            
            // 校验特殊寄存器信息
            assert tombstone.getSpecialRegisterInfo() != null : "特殊寄存器信息为空";
            AArch64Tombstone.SpecialRegisterInfo specialReg = tombstone.getSpecialRegisterInfo();
            assert specialReg.getLr() != null : "LR寄存器为空";
            assert specialReg.getLr() == 0x7d0f7a7fb8L : "LR寄存器값不匹配";
            assert specialReg.getSp() != null : "SP寄存器为空";
            assert specialReg.getSp() == 0x7bdab4f9a0L : "SP寄存器값不匹配";
            assert specialReg.getPc() != null : "PC寄存器为空";
            assert specialReg.getPc() == 0x7d0f79d8ccL : "PC寄存器값不匹配";
            assert specialReg.getPst() != null : "PST寄存器为空";
            assert specialReg.getPst() == 0x60001000L : "PST寄存器값不匹配";
            
            System.out.println("所有校验通过！");
        }
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
            
            // 添加特殊寄存器信息的输出
            if (tombstone.getSpecialRegisterInfo() != null) {
                System.out.println("特殊寄存器信息:");
                AArch64Tombstone.SpecialRegisterInfo specialReg = tombstone.getSpecialRegisterInfo();
                if (specialReg.getLr() != null) {
                    System.out.println("  LR: 0x" + Long.toHexString(specialReg.getLr()));
                }
                if (specialReg.getSp() != null) {
                    System.out.println("  SP: 0x" + Long.toHexString(specialReg.getSp()));
                }
                if (specialReg.getPc() != null) {
                    System.out.println("  PC: 0x" + Long.toHexString(specialReg.getPc()));
                }
                if (specialReg.getPst() != null) {
                    System.out.println("  PST: 0x" + Long.toHexString(specialReg.getPst()));
                }
            }
        }
    }
    
    @Test
    public void testParseBacktraceWithSymbolAndBuildId() {
        AndroidAArch64FileService service = new AndroidAArch64FileService();
        
        // 测试用例：包含符号、偏移量和BuildId的堆栈行
        String stackLine = "#00 pc 000000000005a8cc  /system/lib64/libbinder.so (android::Parcel::ipcSetDataReference(unsigned char const*, unsigned long, unsigned long long const*, unsigned long, void (*)(android::Parcel*, unsigned char const*, unsigned long, unsigned long long const*, unsigned long))+340) (BuildId: f992d84feb3f8b8e5f0f7268aeaa2f5d)";
        
        List<String> lines = Arrays.asList("backtrace:", stackLine);
        AArch64Tombstone tombstone = (AArch64Tombstone) service.parseFile(lines);
        
        assertNotNull(tombstone.getStackDumpInfo());
        assertNotNull(tombstone.getStackDumpInfo().getStackFrames());
        assertFalse(tombstone.getStackDumpInfo().getStackFrames().isEmpty());
        
        AArch64Tombstone.StackDumpInfo.StackFrame frame = tombstone.getStackDumpInfo().getStackFrames().get(0);
        
        // 验证解析结果
        assertEquals(0, frame.getIndex());
        assertEquals(Long.valueOf(0x5a8ccL), frame.getAddress());
        assertEquals("/system/lib64/libbinder.so", frame.getMapsInfo());
        assertEquals("android::Parcel::ipcSetDataReference(unsigned char const*, unsigned long, unsigned long long const*, unsigned long, void (*)(android::Parcel*, unsigned char const*, unsigned long, unsigned long long const*, unsigned long))", frame.getSymbol());
        assertEquals(Long.valueOf(340L), frame.getOffsetFromSymbolStart());
        assertEquals("f992d84feb3f8b8e5f0f7268aeaa2f5d", frame.getBuildId());
    }
    
    @Test
    public void testParseBacktraceWithoutOffset() {
        AndroidAArch64FileService service = new AndroidAArch64FileService();
        
        // 测试用例：只有符号，没有偏移量
        String stackLine = "#01 pc 0000000000064fb4  /system/lib64/libbinder.so (android::IPCThreadState::waitForResponse(android::Parcel*, int*))+780) (BuildId: f992d84feb3f8b8e5f0f7268aeaa2f5d)";
        
        List<String> lines = Arrays.asList("backtrace:", stackLine);
        AArch64Tombstone tombstone = (AArch64Tombstone) service.parseFile(lines);
        
        AArch64Tombstone.StackDumpInfo.StackFrame frame = tombstone.getStackDumpInfo().getStackFrames().get(0);
        
        // 验证解析结果
        assertEquals(1, frame.getIndex());
        assertEquals(Long.valueOf(0x64fb4L), frame.getAddress());
        assertEquals("/system/lib64/libbinder.so", frame.getMapsInfo());
        assertEquals("android::IPCThreadState::waitForResponse(android::Parcel*, int*)", frame.getSymbol());
        assertEquals(Long.valueOf(780L), frame.getOffsetFromSymbolStart());
        assertEquals("f992d84feb3f8b8e5f0f7268aeaa2f5d", frame.getBuildId());
    }
    
    @Test
    public void testParseBacktraceWithBuildIdOnly() {
        AndroidAArch64FileService service = new AndroidAArch64FileService();
        
        // 测试用例：只有BuildId，没有符号
        String stackLine = "#03 pc 000000000005cf80  /system/lib64/libbinder.so (BuildId: f992d84feb3f8b8e5f0f7268aeaa2f5d)";
        
        List<String> lines = Arrays.asList("backtrace:", stackLine);
        AArch64Tombstone tombstone = (AArch64Tombstone) service.parseFile(lines);
        
        AArch64Tombstone.StackDumpInfo.StackFrame frame = tombstone.getStackDumpInfo().getStackFrames().get(0);
        
        // 验证解析结果
        assertEquals(3, frame.getIndex());
        assertEquals(Long.valueOf(0x5cf80L), frame.getAddress());
        assertEquals("/system/lib64/libbinder.so", frame.getMapsInfo());
        assertNull(frame.getSymbol());
        assertNull(frame.getOffsetFromSymbolStart());
        assertEquals("f992d84feb3f8b8e5f0f7268aeaa2f5d", frame.getBuildId());
    }
    
    @Test
    public void testParseBacktraceWithComplexSymbol() {
        AndroidAArch64FileService service = new AndroidAArch64FileService();
        
        // 测试原始问题：复杂的符号名称包含括号和函数指针
        String stackLine = "#00 pc 000000000005a8cc  /system/lib64/libbinder.so (android::Parcel::ipcSetDataReference(unsigned char const*, unsigned long, unsigned long long const*, unsigned long, void (*)(android::Parcel*, unsigned char const*, unsigned long, unsigned long long const*, unsigned long))+340) (BuildId: f992d84feb3f8b8e5f0f7268aeaa2f5d)";
        
        List<String> lines = Arrays.asList("backtrace:", stackLine);
        AArch64Tombstone tombstone = (AArch64Tombstone) service.parseFile(lines);
        
        AArch64Tombstone.StackDumpInfo.StackFrame frame = tombstone.getStackDumpInfo().getStackFrames().get(0);
        
        // 验证符号被正确解析，不包含BuildId
        assertEquals("android::Parcel::ipcSetDataReference(unsigned char const*, unsigned long, unsigned long long const*, unsigned long, void (*)(android::Parcel*, unsigned char const*, unsigned long, unsigned long long const*, unsigned long))", frame.getSymbol());
        assertEquals(Long.valueOf(340L), frame.getOffsetFromSymbolStart());
        assertEquals("f992d84feb3f8b8e5f0f7268aeaa2f5d", frame.getBuildId());
    }
}