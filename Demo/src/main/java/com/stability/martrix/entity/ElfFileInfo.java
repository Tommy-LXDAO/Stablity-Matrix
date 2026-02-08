package com.stability.martrix.entity;

import org.springframework.web.multipart.MultipartFile;

/**
 * ELF文件信息实体
 */
public class ElfFileInfo {
    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private long fileSize;

    /**
     * 文件对象（用于后续分析）
     */
    private MultipartFile file;

    /**
     * 架构信息（arm64, x86_64等）
     */
    private String architecture;

    /**
     * ELF文件类型（可执行文件、共享库、可重定位文件等）
     */
    private String elfType;

    /**
     * 是否为64位文件
     */
    private Boolean is64Bit;

    /**
     * 字节序（大端/小端）
     */
    private String endianness;

    /**
     * 文件在ZIP中的路径（如果来自ZIP）
     */
    private String zipPath;

    public ElfFileInfo() {
    }

    public ElfFileInfo(String fileName, long fileSize, MultipartFile file) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.file = file;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getElfType() {
        return elfType;
    }

    public void setElfType(String elfType) {
        this.elfType = elfType;
    }

    public Boolean getIs64Bit() {
        return is64Bit;
    }

    public void setIs64Bit(Boolean is64Bit) {
        this.is64Bit = is64Bit;
    }

    public String getEndianness() {
        return endianness;
    }

    public void setEndianness(String endianness) {
        this.endianness = endianness;
    }

    public String getZipPath() {
        return zipPath;
    }

    public void setZipPath(String zipPath) {
        this.zipPath = zipPath;
    }

    @Override
    public String toString() {
        return "ElfFileInfo{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", architecture='" + architecture + '\'' +
                ", elfType='" + elfType + '\'' +
                ", is64Bit=" + is64Bit +
                ", endianness='" + endianness + '\'' +
                ", zipPath='" + zipPath + '\'' +
                '}';
    }
}
