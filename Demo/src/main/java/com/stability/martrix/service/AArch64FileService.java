package com.stability.martrix.service;

import com.stability.martrix.annotation.AArch64;
import com.stability.martrix.entity.TroubleEntity;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
@AArch64
public class AArch64FileService implements FileService{

    private static final Logger logger = Logger.getLogger(AArch64FileService.class.getName());
    @Override
    public TroubleEntity parseFile(String filePath) {
        // 在这里实现自己的解析文件逻辑
        return null;
    }
}
