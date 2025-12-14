package com.stability.martrix.service;

import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;

public interface FileService {
    public TroubleEntity parseFile(String filePath);
}
