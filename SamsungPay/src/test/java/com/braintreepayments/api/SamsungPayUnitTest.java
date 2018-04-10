package com.braintreepayments.api;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.braintreepayments.api.exceptions.ConfigurationException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.internal.ClassHelper;
import com.samsung.android.sdk.samsungpay.v2.StatusListener;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

import static com.braintreepayments.api.test.FixturesHelper.stringFromFixture;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({ "org.mockito.*", "org.robolectric.*", "android.*", "org.json.*", "com.samsung.*" })
@PrepareForTest({ ClassHelper.class, SamsungPay.class, com.samsung.android.sdk.samsungpay.v2.SamsungPay.class })
public class SamsungPayUnitTest {

	@Rule
	public PowerMockRule mPowerMockRule = new PowerMockRule();

	private BraintreeFragment mBraintreeFragment;
	private BraintreeErrorListener mErrorListener;

	@Before
	public void setup() throws PackageManager.NameNotFoundException {
		mBraintreeFragment = new MockFragmentBuilder()
				.configuration(stringFromFixture("configuration/with_samsung_pay.json"))
				.build();

		ApplicationInfo mockApplicationInfo = mock(ApplicationInfo.class);

		Bundle mockBundle = mock(Bundle.class);
		when(mockBundle.getFloat(eq("spay_sdk_api_level"))).thenReturn(1.9f);
		mockApplicationInfo.metaData = mockBundle;

		PackageManager mockPackageManager = mock(PackageManager.class);
		when(mockPackageManager.getApplicationInfo(anyString(), anyInt())).thenReturn(mockApplicationInfo);

		Context spiedContext = spy(RuntimeEnvironment.application);
		when(spiedContext.getPackageManager()).thenReturn(mockPackageManager);
		when(mBraintreeFragment.getApplicationContext()).thenReturn(spiedContext);

		mErrorListener = new BraintreeErrorListener() {
			@Override
			public void onError(Exception error) {
				throw new RuntimeException(error);
			}
		};

		mBraintreeFragment.addListener(mErrorListener);
	}

	@Test(timeout = 1000)
	@SuppressWarnings("unchecked")
	public void isReadyToPay_whenSDKNotAvailable_returnsFalse() {
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

	@Test(timeout = 1000)
	public void isReadyToPay_whenSpayStatusIsNotSupported_returnsFalse() throws Exception {
		stubSamsungPayStatus(com.samsung.android.sdk.samsungpay.v2.SamsungPay.SPAY_NOT_SUPPORTED);

		final CountDownLatch latch = new CountDownLatch(1);

		SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean isReadyToPay) {
				assertFalse(isReadyToPay);

				latch.countDown();
			}
		});

		latch.await();
	}

	@Test(timeout = 1000)
	public void isReadyToPay_whenSpayStatusIsNotReady_returnsFalse() throws NoSuchMethodException, InterruptedException {
		stubSamsungPayStatus(com.samsung.android.sdk.samsungpay.v2.SamsungPay.SPAY_NOT_READY);

		final CountDownLatch latch = new CountDownLatch(1);

		SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean isReadyToPay) {
				assertFalse(isReadyToPay);

				latch.countDown();
			}
		});

		latch.await();
	}

	@Test
	public void isReadyToPay_whenSpayStatusIsReady_returnsTrue() throws NoSuchMethodException, InterruptedException {
		stubSamsungPayStatus(com.samsung.android.sdk.samsungpay.v2.SamsungPay.SPAY_READY);

		final CountDownLatch latch = new CountDownLatch(1);

		SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean isReadyToPay) {
				assertTrue(isReadyToPay);

				latch.countDown();
			}
		});

		latch.await();
	}

	@Test
	public void isReadyToPay_onFailure_postsException() throws NoSuchMethodException, InterruptedException {
		stubSamsungPay(new Answer() {
			@Override
			public Object answer(InvocationOnMock invocation) {
				StatusListener listener = (StatusListener) invocation.getArguments()[0];

				listener.onFail(com.samsung.android.sdk.samsungpay.v2.SamsungPay.ERROR_DEVICE_NOT_SAMSUNG, new Bundle());

				return null;
			}
		});

		final CountDownLatch latch = new CountDownLatch(1);

		mBraintreeFragment.removeListener(mErrorListener);

		mBraintreeFragment.addListener(new BraintreeErrorListener() {
			@Override
			public void onError(Exception error) {
				assertTrue(error instanceof ConfigurationException);
				assertEquals("SamsungPay is not available", error.getMessage());

				latch.countDown();
			}
		});

		SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean aBoolean) {

			}
		});

		latch.await();
	}

	@Test
	public void startSamsungPay_setsMerchantId() {

	}

	@Test
	public void startSamsungPay_setsMerchantName() {

	}

	@Test
	public void startSamsungPay_setsAllowedCardBrands() {

	}

	@Test
	public void startSamsungPay_customUI_setsMerchantId() {

	}

	@Test
	public void startSamsungPay_customUI_setsMerchantName() {

	}

	@Test
	public void startSamsungPay_customUI_setsAllowedCardBrands() {

	}

	private void stubSamsungPayStatus(final int status) throws NoSuchMethodException {
		stubSamsungPay(new Answer() {
			@Override
			public Object answer(InvocationOnMock invocation) {
				StatusListener listener = (StatusListener) invocation.getArguments()[0];

				listener.onSuccess(status, new Bundle());

				return null;
			}
		});
	}

	private void stubSamsungPay(Answer answer) throws NoSuchMethodException {
		final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);

		PowerMockito.doAnswer(answer).when(mockSamsungPay).getSamsungPayStatus(any(StatusListener.class));

		Method getSamsungPayStatus = SamsungPay.class.getDeclaredMethod("getSamsungPay", BraintreeFragment.class, BraintreeResponseListener.class);

		PowerMockito.replace(getSamsungPayStatus).with(new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) {
				BraintreeResponseListener<com.samsung.android.sdk.samsungpay.v2.SamsungPay> listener =
						(BraintreeResponseListener<com.samsung.android.sdk.samsungpay.v2.SamsungPay>) args[1];

				listener.onResponse(mockSamsungPay);

				return null;
			}
		});
	}
}
