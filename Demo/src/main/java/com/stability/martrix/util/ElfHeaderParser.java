package com.stability.martrix.util;

/**
 * ELF文件头信息解析工具
 */
public class ElfHeaderParser {

    /**
     * ELF 32/64位标识
     */
    public static final int ELFCLASS32 = 1;
    public static final int ELFCLASS64 = 2;

    /**
     * ELF 数据编码
     */
    public static final int ELFDATA2LSB = 1;  // 小端序
    public static final int ELFDATA2MSB = 2;  // 大端序

    /**
     * ELF 机器类型
     */
    public static final int EM_NONE = 0;
    public static final int EM_386 = 3;       // Intel 80386
    public static final int EM_X86_64 = 62;   // AMD x86-64
    public static final int EM_ARM = 40;      // ARM
    public static final int EM_AARCH64 = 183; // ARM AArch64
    public static final int EM_MIPS = 8;      // MIPS
    public static final int EM_PPC = 20;      // PowerPC
    public static final int EM_PPC64 = 21;    // PowerPC 64-bit
    public static final int EM_RISCV = 243;   // RISC-V

    /**
     * ELF 文件类型
     */
    public static final int ET_NONE = 0;
    public static final int ET_REL = 1;       // 可重定位文件
    public static final int ET_EXEC = 2;      // 可执行文件
    public static final int ET_DYN = 3;       // 共享目标文件
    public static final int ET_CORE = 4;      // 核心转储文件

    /**
     * ELF头信息
     */
    public static class ElfHeaderInfo {
        private boolean is64Bit;
        private boolean isLittleEndian;
        private String architecture;
        private String fileType;
        private int entryPointOffset; // 入口点偏移量（字节）
        private int programHeaderOffset; // 程序头表偏移量
        private int sectionHeaderOffset; // 节头表偏移量

        public boolean is64Bit() {
            return is64Bit;
        }

        public void set64Bit(boolean is64Bit) {
            this.is64Bit = is64Bit;
        }

        public boolean isLittleEndian() {
            return isLittleEndian;
        }

        public void setLittleEndian(boolean isLittleEndian) {
            this.isLittleEndian = isLittleEndian;
        }

        public String getArchitecture() {
            return architecture;
        }

        public void setArchitecture(String architecture) {
            this.architecture = architecture;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }

        public int getEntryPointOffset() {
            return entryPointOffset;
        }

        public void setEntryPointOffset(int entryPointOffset) {
            this.entryPointOffset = entryPointOffset;
        }

        public int getProgramHeaderOffset() {
            return programHeaderOffset;
        }

        public void setProgramHeaderOffset(int programHeaderOffset) {
            this.programHeaderOffset = programHeaderOffset;
        }

        public int getSectionHeaderOffset() {
            return sectionHeaderOffset;
        }

        public void setSectionHeaderOffset(int sectionHeaderOffset) {
            this.sectionHeaderOffset = sectionHeaderOffset;
        }

        @Override
        public String toString() {
            return "ElfHeaderInfo{" +
                    "is64Bit=" + is64Bit +
                    ", isLittleEndian=" + isLittleEndian +
                    ", architecture='" + architecture + '\'' +
                    ", fileType='" + fileType + '\'' +
                    '}';
        }
    }

    /**
     * 解析ELF头信息
     *
     * @param header ELF头字节数组（至少前64字节）
     * @return ELF头信息
     */
    public static ElfHeaderInfo parseElfHeader(byte[] header) {
        if (header == null || header.length < 64) {
            return null;
        }

        // 验证ELF魔数
        if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
            return null;
        }

        ElfHeaderInfo info = new ElfHeaderInfo();

        // EI_CLASS: 1=32位, 2=64位
        int eiClass = header[4] & 0xFF;
        info.set64Bit(eiClass == ELFCLASS64);

        // EI_DATA: 1=小端序, 2=大端序
        int eiData = header[5] & 0xFF;
        info.setLittleEndian(eiData == ELFDATA2LSB);

        // EI_VERSION (版本)
        // header[6]

        // EI_OSABI / EI_ABIVERSION (操作系统ABI / ABI版本)
        // header[7], header[8]

        // e_type (文件类型) - 偏移16
        int eType = readHalf(header, 16, info.isLittleEndian());
        info.setFileType(getFileTypeName(eType));

        // e_machine (机器类型) - 偏移18
        int eMachine = readHalf(header, 18, info.isLittleEndian());
        info.setArchitecture(getArchitectureName(eMachine));

        // e_version (版本) - 偏移20
        // readWord(header, 20, info.isLittleEndian());

        // e_entry (入口点地址) - 偏移24
        if (info.is64Bit()) {
            info.setEntryPointOffset(24);  // 64位: 8字节
            // e_phoff (程序头表偏移) - 偏移32
            info.setProgramHeaderOffset(32);  // 64位: 8字节
            // e_shoff (节头表偏移) - 偏移40
            info.setSectionHeaderOffset(40);  // 64位: 8字节
        } else {
            info.setEntryPointOffset(24);  // 32位: 4字节
            // e_phoff (程序头表偏移) - 偏移28
            info.setProgramHeaderOffset(28);  // 32位: 4字节
            // e_shoff (节头表偏移) - 偏移32
            info.setSectionHeaderOffset(32);  // 32位: 4字节
        }

        return info;
    }

    /**
     * 读取2字节半字
     */
    private static int readHalf(byte[] data, int offset, boolean isLittleEndian) {
        if (isLittleEndian) {
            return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
        } else {
            return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        }
    }

    /**
     * 读取4字节字
     */
    private static long readWord(byte[] data, int offset, boolean isLittleEndian) {
        if (isLittleEndian) {
            return (data[offset] & 0xFFL) |
                   ((data[offset + 1] & 0xFFL) << 8) |
                   ((data[offset + 2] & 0xFFL) << 16) |
                   ((data[offset + 3] & 0xFFL) << 24);
        } else {
            return ((data[offset] & 0xFFL) << 24) |
                   ((data[offset + 1] & 0xFFL) << 16) |
                   ((data[offset + 2] & 0xFFL) << 8) |
                   (data[offset + 3] & 0xFFL);
        }
    }

    /**
     * 获取架构名称
     */
    public static String getArchitectureName(int eMachine) {
        return switch (eMachine) {
            case EM_386 -> "x86 (32-bit)";
            case EM_X86_64 -> "x86_64 (64-bit)";
            case EM_ARM -> "ARM (32-bit)";
            case EM_AARCH64 -> "AArch64 (64-bit)";
            case EM_MIPS -> "MIPS";
            case EM_PPC -> "PowerPC (32-bit)";
            case EM_PPC64 -> "PowerPC (64-bit)";
            case EM_RISCV -> "RISC-V";
            default -> "Unknown (0x" + Integer.toHexString(eMachine) + ")";
        };
    }

    /**
     * 获取文件类型名称
     */
    public static String getFileTypeName(int eType) {
        return switch (eType) {
            case ET_NONE -> "No file type";
            case ET_REL -> "Relocatable file (.o)";
            case ET_EXEC -> "Executable file";
            case ET_DYN -> "Shared object (.so)";
            case ET_CORE -> "Core dump file";
            default -> "Unknown (0x" + Integer.toHexString(eType) + ")";
        };
    }

    /**
     * 获取简单的架构描述
     */
    public static String getSimpleArchitecture(byte[] header) {
        ElfHeaderInfo info = parseElfHeader(header);
        if (info == null) {
            return "Unknown";
        }
        return info.is64Bit() ? "64-bit" : "32-bit";
    }
}
