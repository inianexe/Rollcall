package com.iniexe.rollcall;

final class RollCallAuthState {
    enum Status {
        LOCAL_ONLY,
        SIGNED_OUT,
        AUTHENTICATING,
        SIGNED_IN_UNVERIFIED,
        SIGNED_IN_VERIFIED,
        DEVICE_LINKING_REQUIRED,
        READY_FOR_CLOUD_SYNC,
        RECOVERABLE_ERROR,
        ACCOUNT_DISABLED,
        DELETION_PENDING
    }

    private RollCallAuthState() { }

    static Status resolve(boolean hasLocalData, boolean signedIn, boolean authenticating,
                          boolean emailVerified, boolean deviceLinked, boolean syncEnabled,
                          boolean accountDisabled, boolean deletionPending, boolean recoverableError) {
        if (deletionPending) return Status.DELETION_PENDING;
        if (accountDisabled) return Status.ACCOUNT_DISABLED;
        if (authenticating) return Status.AUTHENTICATING;
        if (recoverableError) return Status.RECOVERABLE_ERROR;
        if (!signedIn) return hasLocalData ? Status.LOCAL_ONLY : Status.SIGNED_OUT;
        if (!emailVerified) return Status.SIGNED_IN_UNVERIFIED;
        if (!deviceLinked || !syncEnabled) return Status.DEVICE_LINKING_REQUIRED;
        return Status.READY_FOR_CLOUD_SYNC;
    }
}
