package com.stability.martrix.service;

import com.stability.martrix.entity.AArch64Tombstone;
import com.stability.martrix.entity.TroubleEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AndroidAbortService {
    // certify if it is really abort
    public boolean certify(TroubleEntity troubleEntity) {
        if (troubleEntity instanceof AArch64Tombstone) {
            AArch64Tombstone aarch64Tombstone = (AArch64Tombstone) troubleEntity;
            List<AArch64Tombstone.StackDumpInfo.StackFrame> stackFrames = aarch64Tombstone.getStackDumpInfo().getStackFrames();
            int size = stackFrames.size();
            if (size == 0) {
                return false;
            }
            AArch64Tombstone.StackDumpInfo.StackFrame stackFrame = stackFrames.get(0);
            if (stackFrame == null) {
                return false;
            }
            String symbol = stackFrame.getSymbol();
            if (symbol == null) {
                // Can do but not now: If a reliable symbol table is available,
                // perform a reverse engineering and check if the assembly instruction is abort.
                return false;
            }
            return symbol.contains("abort");
        } else {
            // Not supported now
            return false;
        }
    }
}
