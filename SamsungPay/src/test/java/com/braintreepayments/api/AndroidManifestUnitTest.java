package com.braintreepayments.api;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static junit.framework.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class AndroidManifestUnitTest {
    private static final float EXPECTED_SPAY_SDK_API_LEVEL = 2.4f;
    private ApplicationInfo mApplicationInfo;

    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        Application application = RuntimeEnvironment.application;
        mApplicationInfo = application.getPackageManager().getApplicationInfo(
                application.getPackageName(),
                PackageManager.GET_META_DATA);
    }

    @Test
    public void hasDebugModeSetToN() {
        assertEquals("N", mApplicationInfo.metaData.getString("debug_mode"));
    }

    @Test
    public void hasSpaySdkApiLevelSet() {
        assertEquals(EXPECTED_SPAY_SDK_API_LEVEL, mApplicationInfo.metaData.getFloat("spay_sdk_api_level"));
    }

    @Test
    public void isExpectedPackage() {
        assertEquals("com.braintreepayments.api.samsungpay", mApplicationInfo.packageName);
    }
}
