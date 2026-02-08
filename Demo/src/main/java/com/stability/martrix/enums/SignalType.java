package com.stability.martrix.enums;

/**
 * Signal type constants for Android crash analysis
 */
public enum SignalType {
    /**
     * Abort signal (usually from abort() call)
     */
    SIGABRT(6, "SIGABRT"),

    /**
     * Bus error (memory alignment issue)
     */
    SIGBUS(7, "SIGBUS"),

    /**
     * Illegal instruction
     */
    SIGILL(4, "SIGILL"),

    /**
     * Floating point exception (divide by zero, overflow, etc.)
     */
    SIGFPE(8, "SIGFPE"),

    /**
     * Broken pipe (write to pipe/socket with no reader)
     */
    SIGPIPE(13, "SIGPIPE"),

    /**
     * Segmentation fault (invalid memory access)
     */
    SIGSEGV(11, "SIGSEGV");

    private final int signalNumber;
    private final String signalName;

    SignalType(int signalNumber, String signalName) {
        this.signalNumber = signalNumber;
        this.signalName = signalName;
    }

    public int getSignalNumber() {
        return signalNumber;
    }

    public String getSignalName() {
        return signalName;
    }
}
