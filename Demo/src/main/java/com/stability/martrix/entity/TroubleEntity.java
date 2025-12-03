package com.stability.martrix.entity;

import lombok.Data;

public class TroubleEntity {
    // 进程号
    private int pid;
    // 首要tid
    private int firstTid;
    // 金成名
    private String processName;
    // 故障版本号
    private String version;
}
