package com.braintreepayments.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.robolectric.RobolectricTestRunner;

import static junit.framework.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({ "org.mockito.*", "org.robolectric.*", "android.*", "org.json.*", "com.visa.*" })
public class SamsungPayUnitTest {

	@Test
	public void isReadyToPay_whenSDKNotAvailable_returnsFalse() {
		fail("not implemented");
	}

	@Test
	public void isReadyToPay_whenSDKNotAvailable_postsException() {
		fail("not implemented");
	}
}
