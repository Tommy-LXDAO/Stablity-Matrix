package com.stability.martrix.enums;

public enum CPUArchitecture {
    // 基础架构
    X86("x86", "Intel 32位架构", 32, 1985),
    X86_64("x64", "AMD64/Intel 64位扩展", 64, 2003),
    ARM("ARM", "Acorn RISC Machine", 32, 1985),
    ARM64("ARM64", "ARM 64位架构", 64, 2011),
    MIPS("MIPS", "无互锁流水线级微处理器", 32, 1981),
    POWER("Power", "IBM性能优化增强RISC", 64, 1990),
    RISC_V("RISC-V", "开源指令集架构", 64, 2010),
    SPARC("SPARC", "可扩展处理器架构", 64, 1987),
    IA64("IA-64", "Intel安腾架构", 64, 2001);

    // 枚举字段
    private final String code;
    private final String description;
    private final int bits;
    private final int yearIntroduced;

    // 构造函数
    CPUArchitecture(String code, String description, int bits, int yearIntroduced) {
        this.code = code;
        this.description = description;
        this.bits = bits;
        this.yearIntroduced = yearIntroduced;
    }

    // Getter方法
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public int getBits() { return bits; }
    public int getYearIntroduced() { return yearIntroduced; }
}
