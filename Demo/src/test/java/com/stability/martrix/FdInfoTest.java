package com.stability.martrix;

import com.stability.martrix.entity.AArch64Tombstone;

public class FdInfoTest {
    public static void main(String[] args) {
        // 测试FdsanInfo解析
        
        // 测试未拥有的文件描述符
        String unowned = "(unowned)";
        AArch64Tombstone.FdInfo.FdsanInfo fdsanInfo1 = AArch64Tombstone.FdInfo.parseFdsanInfo(unowned);
        System.out.println("未拥有的Fd: " + fdsanInfo1); // 应该为null
        
        // 测试拥有的文件描述符
        String owned = "(owned by unique_fd 0x7c65904a74)";
        AArch64Tombstone.FdInfo.FdsanInfo fdsanInfo2 = AArch64Tombstone.FdInfo.parseFdsanInfo(owned);
        if (fdsanInfo2 != null) {
            System.out.println("拥有的Fd - 类型: " + fdsanInfo2.getOwnedType() + ", 所有者: 0x" + Long.toHexString(fdsanInfo2.getOwner()));
        }
        
        // 测试拥有的Socket文件描述符
        String ownedSocket = "(owned by SocketImpl 0x6481d07)";
        AArch64Tombstone.FdInfo.FdsanInfo fdsanInfo3 = AArch64Tombstone.FdInfo.parseFdsanInfo(ownedSocket);
        if (fdsanInfo3 != null) {
            System.out.println("拥有的Socket - 类型: " + fdsanInfo3.getOwnedType() + ", 所有者: 0x" + Long.toHexString(fdsanInfo3.getOwner()));
        }
        
        // 测试拥有的ZipArchive文件描述符
        String ownedZip = "(owned by ZipArchive 0x7c6cdedb40)";
        AArch64Tombstone.FdInfo.FdsanInfo fdsanInfo4 = AArch64Tombstone.FdInfo.parseFdsanInfo(ownedZip);
        if (fdsanInfo4 != null) {
            System.out.println("拥有的ZipArchive - 类型: " + fdsanInfo4.getOwnedType() + ", 所有者: 0x" + Long.toHexString(fdsanInfo4.getOwner()));
        }
        
        // 测试null输入
        AArch64Tombstone.FdInfo.FdsanInfo fdsanInfo5 = AArch64Tombstone.FdInfo.parseFdsanInfo(null);
        System.out.println("Null输入: " + fdsanInfo5); // 应该为null
        
        // 测试空字符串输入
        AArch64Tombstone.FdInfo.FdsanInfo fdsanInfo6 = AArch64Tombstone.FdInfo.parseFdsanInfo("");
        System.out.println("空字符串输入: " + fdsanInfo6); // 应该为null
    }
}