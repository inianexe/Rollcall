package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RollCallAuthStateTest {
    @Test public void localUseDoesNotRequireAnAccount() {
        assertEquals(RollCallAuthState.Status.LOCAL_ONLY,
                RollCallAuthState.resolve(true, false, false, false, false, false, false, false, false));
    }

    @Test public void verifiedAccountNeedsDeviceAndSyncConsentBeforeCloudUse() {
        assertEquals(RollCallAuthState.Status.DEVICE_LINKING_REQUIRED,
                RollCallAuthState.resolve(true, true, false, true, true, false, false, false, false));
        assertEquals(RollCallAuthState.Status.READY_FOR_CLOUD_SYNC,
                RollCallAuthState.resolve(true, true, false, true, true, true, false, false, false));
    }

    @Test public void securityStatesTakePriorityOverOrdinaryAuthState() {
        assertEquals(RollCallAuthState.Status.DELETION_PENDING,
                RollCallAuthState.resolve(true, true, false, true, true, true, true, true, true));
        assertEquals(RollCallAuthState.Status.ACCOUNT_DISABLED,
                RollCallAuthState.resolve(true, true, false, true, true, true, true, false, true));
        assertEquals(RollCallAuthState.Status.SIGNED_IN_UNVERIFIED,
                RollCallAuthState.resolve(true, true, false, false, true, true, false, false, false));
    }
}
