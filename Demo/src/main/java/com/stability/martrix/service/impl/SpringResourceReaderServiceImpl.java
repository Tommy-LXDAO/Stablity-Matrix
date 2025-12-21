package com.stability.martrix.service.impl;

import com.stability.martrix.service.ResourceReaderService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Spring资源读取服务实现
 */
@Service
public class SpringResourceReaderServiceImpl implements ResourceReaderService {
    private static final Logger logger = Logger.getLogger(SpringResourceReaderServiceImpl.class.getName());

    @Override
    public List<String> readLinesFromResource(String resourcePath) {
        List<String> lines = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            InputStream inputStream = resource.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
        } catch (Exception e) {
            logger.warning("读取资源文件失败: " + resourcePath + ", 错误信息: " + e.getMessage());
        }
        return lines;
    }
}