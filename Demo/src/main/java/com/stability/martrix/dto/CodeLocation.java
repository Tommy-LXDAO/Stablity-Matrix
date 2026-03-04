package com.stability.martrix.dto;

import java.util.List;

/**
 * 二进制地址对应的代码位置信息
 */
public class CodeLocation {

    /**
     * 源文件路径
     */
    private String sourceFile;

    /**
     * 行号
     */
    private int lineNumber;

    /**
     * 函数名
     */
    private String functionName;

    /**
     * 列号（可选）
     */
    private Integer columnNumber;

    /**
     * 代码片段
     */
    private String codeSnippet;

    public CodeLocation() {
    }

    public CodeLocation(String sourceFile, int lineNumber, String functionName) {
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
        this.functionName = functionName;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public Integer getColumnNumber() {
        return columnNumber;
    }

    public void setColumnNumber(Integer columnNumber) {
        this.columnNumber = columnNumber;
    }

    public String getCodeSnippet() {
        return codeSnippet;
    }

    public void setCodeSnippet(String codeSnippet) {
        this.codeSnippet = codeSnippet;
    }
}
