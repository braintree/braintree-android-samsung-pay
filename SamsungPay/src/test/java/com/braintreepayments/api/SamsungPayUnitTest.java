package com.braintreepayments.api;

import com.braintreepayments.api.exceptions.ConfigurationException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.internal.ClassHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CountDownLatch;

import static com.braintreepayments.api.test.FixturesHelper.stringFromFixture;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({ "org.mockito.*", "org.robolectric.*", "android.*", "org.json.*", "com.samsung.*" })
@PrepareForTest({ ClassHelper.class })
public class SamsungPayUnitTest {

	@Rule
	public PowerMockRule mPowerMockRule = new PowerMockRule();

	private BraintreeFragment mBraintreeFragment;

	@Before
	public void setup() {
		mBraintreeFragment = new MockFragmentBuilder()
				.configuration(stringFromFixture("configuration/with_samsung_pay.json"))
				.build();

		mBraintreeFragment.addListener(new BraintreeErrorListener() {
			@Override
			public void onError(Exception error) {
				throw new RuntimeException(error);
			}
		});
	}

	@Test(timeout = 1000)
	@SuppressWarnings("unchecked")
	public void isReadyToPay_whenSDKNotAvailable_returnsFalse() throws ClassNotFoundException {
		mockStatic(ClassHelper.class);
		when(ClassHelper.isClassAvailable(eq("com.samsung.android.sdk.samsungpay.v2.SamsungPay"))).thenReturn(false);

		final CountDownLatch latch = new CountDownLatch(1);

		SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean aBoolean) {
				assertFalse(aBoolean);

				latch.countDown();
			}
		});
	}

	@Test//(timeout = 1000)
	public void isReadyToPay_whenSDKNotAvailable_postsException() throws InterruptedException {
		mockStatic(ClassHelper.class);
		when(ClassHelper.isClassAvailable(eq("com.samsung.android.sdk.samsungpay.v2.SamsungPay"))).thenReturn(false);

		BraintreeFragment braintreeFragment = new MockFragmentBuilder()
				.build();
		final CountDownLatch latch = new CountDownLatch(1);

		SamsungPay.isReadyToPay(braintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean aBoolean) {
				assertFalse(aBoolean);
				latch.countDown();
			}
		});

		ArgumentCaptor<ConfigurationException> argumentCaptor = ArgumentCaptor.forClass(ConfigurationException.class);
		verify(braintreeFragment, times(1)).postCallback(argumentCaptor.capture());
		assertEquals("TODO final value", argumentCaptor.getValue().getMessage());

		latch.await();
	}

	@Test
	public void isReadyToPay_whenSpayStatusIsNotSupported_returnsFalse() {
		fail("not implemented");
	}

	@Test
	public void isReadToPay_whenSpayStatusIsNotReady_returnsFalse() {
		fail("not implemented");
	}

	@Test
	public void isReadyToPay_whenSpayStatusIsReady_returnsTrue() {
		fail("not implemented");
	}

	@Test
	public void createPaymentBuilder_returnsPaymentBuilderWithMerchantName() {
		fail("not implemented");
	}

	@Test
	public void createPaymentBuilder_returnsPaymentBuilderWithMerchantId() {
		fail("not implemented");
	}

	@Test
	public void createPaymentBuilder_returnsPaymentBuilderWithAcceptedCardBrands() {
		fail("not implemented");
	}
}
