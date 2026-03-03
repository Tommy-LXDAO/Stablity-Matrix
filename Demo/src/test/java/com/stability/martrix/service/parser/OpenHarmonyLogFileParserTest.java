package com.stability.martrix.service.parser;

import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import com.stability.martrix.entity.register.AArch64RegisterDumpInfo;
import com.stability.martrix.enums.CPUArchitecture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenHarmony 日志解析器测试
 */
@SpringBootTest
public class OpenHarmonyLogFileParserTest {

    private final OpenHarmonyLogFileParser parser = new OpenHarmonyLogFileParser();

    @Test
    public void testCanParse() throws Exception {
        // 读取测试文件
        List<String> lines = Files.readAllLines(
            Paths.get("/Users/lyw/IdeaProjects/Stablity-Matrix/datas/openharmony/cppcrash-strptime-0-20220706181725487.log"));

        // 测试 canParse
        assertTrue(parser.canParse(lines), "应该能够解析 OpenHarmony 日志");

        // 测试不匹配的日志
        assertFalse(parser.canParse(List.of("some random text")), "不应该解析非 OpenHarmony 日志");
        assertFalse(parser.canParse(List.of()), "空列表应该返回 false");
        assertFalse(parser.canParse(null), "null 应该返回 false");
    }

    @Test
    public void testParseOpenHarmonyLog() throws Exception {
        // 读取测试文件
        List<String> lines = Files.readAllLines(
            Paths.get("/Users/lyw/IdeaProjects/Stablity-Matrix/datas/openharmony/cppcrash-strptime-0-20220706181725487.log"));

        // 解析
        TroubleEntity entity = parser.parse(lines);

        // 验证基本解析
        assertNotNull(entity, "解析结果不应为空");
        assertTrue(entity instanceof AArch64Tombstone, "应该是 AArch64Tombstone 类型");

        AArch64Tombstone tombstone = (AArch64Tombstone) entity;

        // 验证基本信息
        assertEquals(18298, tombstone.getPid(), "PID 应该为 18298");
        assertEquals("strptime", tombstone.getProcessName(), "进程名应该为 strptime");
        assertEquals(18298, tombstone.getFirstTid(), "TID 应该为 18298");
        assertEquals(CPUArchitecture.ARM64, tombstone.getCpuArchitecture(), "应该是 ARM64 架构");

        // 验证 Build info 作为 version
        assertNotNull(tombstone.getVersion(), "Version 不应为空");
        assertEquals("OpenHarmony 6.1.0.32", tombstone.getVersion(), "Version 应该为 OpenHarmony 6.1.0.32");

        // 验证信号信息
        assertNotNull(tombstone.getSignalInfo(), "信号信息不应为空");
        assertEquals(11, tombstone.getSignalInfo().getSigNumber(), "信号编号应该是 11 (SIGSEGV)");
        assertNotNull(tombstone.getSignalInfo().getSigInformation(), "信号信息不应为空");
        assertTrue(tombstone.getSignalInfo().getSigInformation().contains("SIGSEGV"), "信号信息应包含 SIGSEGV");

        // 验证寄存器信息 - ARM32 转 ARM64
        assertNotNull(tombstone.getRegisterDumpInfo(), "寄存器信息不应为空");
        AArch64RegisterDumpInfo regs = tombstone.getRegisterDumpInfo();

        // 验证 ARM32 r0 (0x4ceadb00) -> ARM64 x0
        assertEquals(0x4ceadb00L, regs.getX0(), "x0 应该是 0x4ceadb00");

        // 验证 ARM32 r1 (0xffc480a8) -> ARM64 x1
        assertEquals(0xffc480a8L, regs.getX1(), "x1 应该是 0xffc480a8");

        // 验证 ARM32 sp (0xffc47f80) -> ARM64 sp
        assertEquals(0xffc47f80L, regs.getSp(), "sp 应该是 0xffc47f80");

        // 验证 ARM32 pc (0xf7cfc8d4) -> ARM64 pc
        assertEquals(0xf7cfc8d4L, regs.getPc(), "pc 应该是 0xf7cfc8d4");

        // 验证 ARM32 lr (0x00bc96c5) -> ARM64 x30
        assertEquals(0x00bc96c5L, regs.getX30(), "x30 (lr) 应该是 0x00bc96c5");

        // 验证 ARM32 fp (0xffc48020) -> ARM64 x29
        assertEquals(0xffc48020L, regs.getX29(), "x29 (fp) 应该是 0xffc48020");

        // 验证堆栈帧
        assertNotNull(tombstone.getStackDumpInfo(), "堆栈信息不应为空");
        assertNotNull(tombstone.getStackDumpInfo().getStackFrames(), "堆栈帧列表不应为空");
        assertTrue(tombstone.getStackDumpInfo().getStackFrames().size() > 0, "应该有堆栈帧");

        // 验证第一个堆栈帧
        AArch64Tombstone.StackDumpInfo.StackFrame firstFrame = tombstone.getStackDumpInfo().getStackFrames().get(0);
        assertEquals(0, firstFrame.getIndex(), "第一个堆栈帧索引应该是 0");
        assertEquals(0xef8d4L, firstFrame.getAddress(), "第一个堆栈帧地址应该是 0xef8d4");

        // 验证 Maps 信息
        assertNotNull(tombstone.getMapsInfoList(), "Maps 信息不应为空");
        assertTrue(tombstone.getMapsInfoList().size() > 0, "应该有 Maps 信息");

        // 验证特殊寄存器
        assertNotNull(tombstone.getSpecialRegisterInfo(), "特殊寄存器信息不应为空");
        assertEquals(regs.getX30(), tombstone.getSpecialRegisterInfo().getLr(), "LR 应该匹配");
        assertEquals(regs.getSp(), tombstone.getSpecialRegisterInfo().getSp(), "SP 应该匹配");
        assertEquals(regs.getPc(), tombstone.getSpecialRegisterInfo().getPc(), "PC 应该匹配");

        System.out.println("OpenHarmony 日志解析测试通过！");
        System.out.println("PID: " + tombstone.getPid());
        System.out.println("Process: " + tombstone.getProcessName());
        System.out.println("Signal: " + tombstone.getSignalInfo().getSigNumber());
        System.out.println("Stack frames: " + tombstone.getStackDumpInfo().getStackFrames().size());
        System.out.println("Maps entries: " + tombstone.getMapsInfoList().size());
        System.out.println("x0: 0x" + Long.toHexString(regs.getX0()));
        System.out.println("x1: 0x" + Long.toHexString(regs.getX1()));
        System.out.println("sp: 0x" + Long.toHexString(regs.getSp()));
        System.out.println("pc: 0x" + Long.toHexString(regs.getPc()));
    }

    @Test
    public void testParseAnotherLog() throws Exception {
        // 测试另一个日志文件
        List<String> lines = Files.readAllLines(
            Paths.get("/Users/lyw/IdeaProjects/Stablity-Matrix/datas/openharmony/cppcrash-strptime-0-20260224175202902.log"));

        TroubleEntity entity = parser.parse(lines);

        assertNotNull(entity, "解析结果不应为空");
        assertTrue(entity instanceof AArch64Tombstone);

        AArch64Tombstone tombstone = (AArch64Tombstone) entity;

        // 验证 SIGABRT 信号
        assertEquals(14707, tombstone.getPid(), "PID 应该为 14707");
        assertEquals(6, tombstone.getSignalInfo().getSigNumber(), "信号编号应该是 6 (SIGABRT)");
    }

    @Test
    public void testIsValid() {
        // 测试 isValid 方法
        AArch64Tombstone validEntity = new AArch64Tombstone();
        validEntity.setPid(12345);
        assertTrue(parser.isValid(validEntity), "有 PID 的实体应该有效");

        assertFalse(parser.isValid(null), "null 应该无效");
        assertFalse(parser.isValid(new AArch64Tombstone()), "没有 PID 的实体应该无效");
    }

    @Test
    public void testGetPlatformName() {
        assertEquals("OpenHarmony", parser.getPlatformName());
        assertEquals(20, parser.getPriority());
    }
}
