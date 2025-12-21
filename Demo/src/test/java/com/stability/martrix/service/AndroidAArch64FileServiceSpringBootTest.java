package com.stability.martrix.service;

import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AndroidAArch64FileServiceSpringBootTest {

    @Autowired
    private AndroidAArch64FileService androidAArch64FileService;

    @Test
    void testParseFileWithList() throws Exception {
        // 从文件读取内容到List<String>
        List<String> lines = Files.readAllLines(
                Paths.get("src/main/resources/tombstone_00"));

        // 使用服务解析
        TroubleEntity entity = androidAArch64FileService.parseFile(lines);

        // 校验解析结果
        validateTombstoneInfo(entity);
    }

    private void validateTombstoneInfo(TroubleEntity entity) {
        assertNotNull(entity, "实体不应为空");
        assertInstanceOf(AArch64Tombstone.class, entity, "实体应为AArch64Tombstone类型");

        AArch64Tombstone tombstone = (AArch64Tombstone) entity;

        // 校验基本信息
        assertEquals("com.apkpure.aegon", tombstone.getProcessName(), "进程名不匹配");
        assertEquals(16369, tombstone.getPid(), "PID不匹配");
        assertEquals(16369, tombstone.getFirstTid(), "TID不匹配");

        // 校验信号信息
        assertNotNull(tombstone.getSignalInfo(), "信号信息为空");
        assertEquals(11, tombstone.getSignalInfo().getSigNumber(), "信号编号不匹配");
        assertEquals("SIGSEGV", tombstone.getSignalInfo().getSigInformation(), "信号信息不匹配");
        assertEquals("SEGV_MAPERR", tombstone.getSignalInfo().getTroubleInformation(), "故障信息不匹配");
        assertNotNull(tombstone.getSignalInfo().getFaultAddress(), "故障地址为空");
        assertEquals(0x7c5072d048L, tombstone.getSignalInfo().getFaultAddress(), "故障地址不匹配");

        // 校验堆栈信息
        assertNotNull(tombstone.getStackDumpInfo(), "堆栈信息为空");
        assertNotNull(tombstone.getStackDumpInfo().getStackFrames(), "堆栈帧为空");
        assertTrue(tombstone.getStackDumpInfo().getStackFrames().size() >= 4, "堆栈帧数量不足");

        // 校验具体的堆栈帧信息
        List<AArch64Tombstone.StackDumpInfo.StackFrame> frames = tombstone.getStackDumpInfo().getStackFrames();
        // 第0个堆栈帧
        assertEquals(0, frames.get(0).getIndex(), "第一个堆栈帧索引不正确");
        assertEquals(0x5a8ccL, frames.get(0).getAddress(), "第一个堆栈帧地址不正确");
        assertEquals("/system/lib64/libbinder.so", frames.get(0).getMapsInfo(), "第一个堆栈帧映射信息不正确");
        assertNotNull(frames.get(0).getSymbol(), "第一个堆栈帧符号信息为空");
        assertTrue(frames.get(0).getSymbol().contains("android::Parcel::ipcSetDataReference"), "第一个堆栈帧符号信息不正确");
        
        // 第1个堆栈帧
        assertEquals(1, frames.get(1).getIndex(), "第二个堆栈帧索引不正确");
        assertEquals(0x64fb4L, frames.get(1).getAddress(), "第二个堆栈帧地址不正确");
        assertEquals("/system/lib64/libbinder.so", frames.get(1).getMapsInfo(), "第二个堆栈帧映射信息不正确");
        assertNotNull(frames.get(1).getSymbol(), "第二个堆栈帧符号信息为空");
        assertTrue(frames.get(1).getSymbol().contains("android::IPCThreadState::waitForResponse"), "第二个堆栈帧符号信息不正确");
        
        // 第2个堆栈帧
        assertEquals(2, frames.get(2).getIndex(), "第三个堆栈帧索引不正确");
        assertEquals(0x597acL, frames.get(2).getAddress(), "第三个堆栈帧地址不正确");
        assertEquals("/system/lib64/libbinder.so", frames.get(2).getMapsInfo(), "第三个堆栈帧映射信息不正确");
        assertNotNull(frames.get(2).getSymbol(), "第三个堆栈帧符号信息为空");
        assertTrue(frames.get(2).getSymbol().contains("android::IPCThreadState::transact"), "第三个堆栈帧符号信息不正确");
        
        // 第3个堆栈帧 (没有符号信息)
        assertEquals(3, frames.get(3).getIndex(), "第四个堆栈帧索引不正确");
        assertEquals(0x5cf80L, frames.get(3).getAddress(), "第四个堆栈帧地址不正确");
        assertEquals("/system/lib64/libbinder.so", frames.get(3).getMapsInfo(), "第四个堆栈帧映射信息不正确");
        assertNull(frames.get(3).getSymbol(), "第四个堆栈帧符号信息应该为空");

        // 校验寄存器信息
        assertNotNull(tombstone.getRegisterDumpInfo(), "寄存器信息为空");
        // 校验所有通用寄存器
        assertEquals(0, tombstone.getRegisterDumpInfo().getX0(), "X0寄存器值不匹配");
        assertEquals(0x7c5072d028L, tombstone.getRegisterDumpInfo().getX1(), "X1寄存器值不匹配");
        assertEquals(0x20L, tombstone.getRegisterDumpInfo().getX2(), "X2寄存器值不匹配");
        assertEquals(0x7c5072d048L, tombstone.getRegisterDumpInfo().getX3(), "X3寄存器值不匹配");
        assertEquals(0x1L, tombstone.getRegisterDumpInfo().getX4(), "X4寄存器值不匹配");
        assertEquals(0x0L, tombstone.getRegisterDumpInfo().getX5(), "X5寄存器值不匹配");
        assertEquals(0x0L, tombstone.getRegisterDumpInfo().getX6(), "X6寄存器值不匹配");
        assertEquals(0x0L, tombstone.getRegisterDumpInfo().getX7(), "X7寄存器值不匹配");
        assertEquals(0x0L, tombstone.getRegisterDumpInfo().getX8(), "X8寄存器值不匹配");
        assertEquals(0x73682a85L, tombstone.getRegisterDumpInfo().getX9(), "X9寄存器值不匹配");
        assertEquals(0x66642a85L, tombstone.getRegisterDumpInfo().getX10(), "X10寄存器值不匹配");
        assertEquals(0x73622a85L, tombstone.getRegisterDumpInfo().getX11(), "X11寄存器值不匹配");
        assertEquals(0x7c5072d028L, tombstone.getRegisterDumpInfo().getX12(), "X12寄存器值不匹配");
        assertEquals(0x7c5072d048L, tombstone.getRegisterDumpInfo().getX13(), "X13寄存器值不匹配");
        assertEquals(0x7bdab4fcc0L, tombstone.getRegisterDumpInfo().getX14(), "X14寄存器值不匹配");
        assertEquals(0x0L, tombstone.getRegisterDumpInfo().getX15(), "X15寄存器值不匹配");
        assertEquals(0x7d0f7faf50L, tombstone.getRegisterDumpInfo().getX16(), "X16寄存器值不匹配");
        assertEquals(0x7d09b42400L, tombstone.getRegisterDumpInfo().getX17(), "X17寄存器值不匹配");
        assertEquals(0x7bda2d0000L, tombstone.getRegisterDumpInfo().getX18(), "X18寄存器值不匹配");
        assertEquals(0x7c50ab0180L, tombstone.getRegisterDumpInfo().getX19(), "X19寄存器值不匹配");
        assertEquals(0x1L, tombstone.getRegisterDumpInfo().getX20(), "X20寄存器值不匹配");
        assertEquals(0x7c5072d048L, tombstone.getRegisterDumpInfo().getX21(), "X21寄存器值不匹配");
        assertEquals(0x7c5072d028L, tombstone.getRegisterDumpInfo().getX22(), "X22寄存器值不匹配");
        assertEquals(0x7d0f7a87fcL, tombstone.getRegisterDumpInfo().getX23(), "X23寄存器值不匹配");
        assertEquals(0x20L, tombstone.getRegisterDumpInfo().getX24(), "X24寄存器值不匹配");
        assertEquals(0x7bdab51000L, tombstone.getRegisterDumpInfo().getX25(), "X25寄存器值不匹配");
        assertEquals(0x7c50ab0188L, tombstone.getRegisterDumpInfo().getX26(), "X26寄存器值不匹配");
        assertEquals(0x7c50ab01a8L, tombstone.getRegisterDumpInfo().getX27(), "X27寄存器值不匹配");
        assertEquals(0x7d0f7fc000L, tombstone.getRegisterDumpInfo().getX28(), "X28寄存器值不匹配");
        assertEquals(0x7bdab4f9d0L, tombstone.getRegisterDumpInfo().getX29(), "X29寄存器值不匹配");

        // 校验特殊寄存器信息
        assertNotNull(tombstone.getSpecialRegisterInfo(), "特殊寄存器信息为空");
        AArch64Tombstone.SpecialRegisterInfo specialReg = tombstone.getSpecialRegisterInfo();
        assertNotNull(specialReg.getLr(), "LR寄存器为空");
        assertEquals(0x7d0f7a7fb8L, specialReg.getLr(), "LR寄存器值不匹配");
        assertNotNull(specialReg.getSp(), "SP寄存器为空");
        assertEquals(0x7bdab4f9a0L, specialReg.getSp(), "SP寄存器值不匹配");
        assertNotNull(specialReg.getPc(), "PC寄存器为空");
        assertEquals(0x7d0f79d8ccL, specialReg.getPc(), "PC寄存器值不匹配");
        assertNotNull(specialReg.getPst(), "PST寄存器为空");
        assertEquals(0x60001000L, specialReg.getPst(), "PST寄存器值不匹配");
    }
}