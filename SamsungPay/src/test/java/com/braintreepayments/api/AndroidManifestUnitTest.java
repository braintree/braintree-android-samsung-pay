package com.braintreepayments.api;

import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.braintreepayments.api.samsungpay.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class AndroidManifestUnitTest {
    private static final float EXPECTED_SPAY_SDK_API_LEVEL = 2.5f;
    private Bundle metaData;


    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        Application application = RuntimeEnvironment.application;
        metaData = application.getPackageManager().getApplicationInfo(
                application.getPackageName(),
                PackageManager.GET_META_DATA).metaData;
    }

    @Test
    public void hasDebugModeSetToN() {
        assertEquals("N", metaData.getString("debug_mode"));
    }

    @Test
    public void hasSpaySdkApiLevelSet() {
        assertEquals(EXPECTED_SPAY_SDK_API_LEVEL, metaData.getFloat("spay_sdk_api_level"));
    }
}
