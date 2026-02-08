package com.stability.martrix.entity;

import lombok.Data;

@Data
public class TroubleEntity {
    // 进程号
    private Integer pid;
    // 首要tid
    private Integer firstTid;
    // 进程名
    private String processName;
    // 故障版本号
    private String version;
}
