package com.iniexe.rollcall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReleaseUpdateNotifierTest {
    @Test public void updatePolicyDefaultsToOptionalAndRecognizesPublishedMetadata() {
        assertEquals("optional", ReleaseUpdateNotifier.parsePolicy(null));
        assertEquals("optional", ReleaseUpdateNotifier.parsePolicy("A release without a policy"));
        assertEquals("recommended", ReleaseUpdateNotifier.parsePolicy(
                "<!-- rollcall-update-policy: recommended -->\n\nChanges"));
        assertEquals("required", ReleaseUpdateNotifier.parsePolicy(
                "<!-- rollcall-update-policy: required -->\n\nSecurity update"));
    }
}
