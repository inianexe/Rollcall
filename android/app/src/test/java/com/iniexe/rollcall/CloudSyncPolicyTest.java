package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CloudSyncPolicyTest {
    @Test public void cloudUploadsRequireExplicitMatchingAccountOwnership() {
        assertFalse(CloudSyncPolicy.allowsUpload("account-a", null));
        assertFalse(CloudSyncPolicy.allowsUpload("account-a", ""));
        assertFalse(CloudSyncPolicy.allowsUpload("account-a", "account-b"));
        assertFalse(CloudSyncPolicy.allowsUpload(null, "account-a"));
        assertTrue(CloudSyncPolicy.allowsUpload("account-a", "account-a"));
    }
}
