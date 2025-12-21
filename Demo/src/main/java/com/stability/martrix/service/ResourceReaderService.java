package com.stability.martrix.service;

import java.util.List;

/**
 * 资源读取服务接口
 */
public interface ResourceReaderService {
    /**
     * 从资源路径读取文件内容并转换为字符串列表
     * @param resourcePath 资源路径
     * @return 文件内容的字符串列表
     */
    List<String> readLinesFromResource(String resourcePath);
}