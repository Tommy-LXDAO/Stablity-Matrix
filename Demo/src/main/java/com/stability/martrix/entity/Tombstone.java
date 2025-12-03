package com.stability.martrix.entity;

import com.stability.martrix.enums.CPUArchitecture;

public class Tombstone extends TroubleEntity {
    private String stackTopRawInformation;
    private int sigNumber;
    private String sigInformation;
    private String troubleInformation;
    private CPUArchitecture cpuArchitecture;
}
