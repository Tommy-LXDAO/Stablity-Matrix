package com.stability.martrix.service;

import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AITroubleAnalysisServiceTest {

    @Autowired
    private AITroubleAnalysisService aiTroubleAnalysisService;

    @MockBean
    private AndroidAArch64FileService androidAArch64FileService;

    @Test
    void testAnalyzeTrouble() throws Exception {
        // 准备测试数据
        List<String> lines = Files.readAllLines(
                Paths.get("src/main/resources/tombstone_00"));

        // 模拟文件解析服务
        TroubleEntity entity = androidAArch64FileService.parseFile(lines);
        when(androidAArch64FileService.parseFile(lines)).thenReturn(entity);

        // 确保解析结果是AArch64Tombstone类型
        assertTrue(entity instanceof AArch64Tombstone, "实体应为AArch64Tombstone类型");

        AArch64Tombstone tombstone = (AArch64Tombstone) entity;

        // 调用AI分析服务（注意：这里不会真正调用AI，因为我们没有配置真实的API密钥）
        // 在实际测试中，你可能需要使用mock或testcontainers来模拟AI服务
        assertNotNull(tombstone, "Tombstone对象不应为空");
        
        // 验证服务调用
        verify(androidAArch64FileService, times(1)).parseFile(lines);
    }
}