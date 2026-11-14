package com.iniexe.rollcall;

final class CloudSyncPolicy {
    private CloudSyncPolicy() { }

    static boolean allowsUpload(String authenticatedUid, String syncOwnerUid) {
        return authenticatedUid != null && !authenticatedUid.isEmpty()
                && authenticatedUid.equals(syncOwnerUid);
    }
}
