package com.stability.martrix.service;

import com.stability.martrix.entity.TroubleEntity;

import java.util.List;

public interface FileService {
    TroubleEntity parseFile(String filePath);
    
    /**
     * 解析字符串列表形式的文件内容
     * @param lines 文件内容的字符串列表
     * @return 解析后的TroubleEntity对象
     */
    TroubleEntity parseFile(List<String> lines);
}