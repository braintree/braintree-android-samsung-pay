package com.braintreepayments.api;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.braintreepayments.api.exceptions.SamsungPayException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.internal.ClassHelper;
import com.samsung.android.sdk.samsungpay.v2.StatusListener;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({ "org.mockito.*", "org.robolectric.*", "android.*", "org.json.*", "com.samsung.*" })
@PrepareForTest({ ClassHelper.class, SamsungPay.class,
		com.samsung.android.sdk.samsungpay.v2.SamsungPay.class, PaymentManager.class })
public class SamsungPayUnitTest {

	@Rule
	public PowerMockRule mPowerMockRule = new PowerMockRule();

	private BraintreeFragment mBraintreeFragment;

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

		mBraintreeFragment.addListener(new BraintreeErrorListener() {
			@Override
			public void onError(Exception error) {
				throw new RuntimeException(error);
			}
		});
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

		SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean aBoolean) {
				assertFalse(aBoolean);
				latch.countDown();
			}
		});

		latch.await();

		ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
		verify(mBraintreeFragment).postCallback(exceptionCaptor.capture());

		Exception capturedException = exceptionCaptor.getValue();

		assertTrue(capturedException instanceof SamsungPayException);
		assertEquals(com.samsung.android.sdk.samsungpay.v2.SamsungPay.ERROR_DEVICE_NOT_SAMSUNG, ((SamsungPayException) capturedException).getCode());
		assertNotNull(((SamsungPayException) capturedException).getExtras());
	}

	@Test
	public void startSamsungPay_setsMerchantId() throws NoSuchMethodException {
		PaymentInfo.Builder info = new PaymentInfo.Builder()
				.setAmount(new PaymentInfo.Amount.Builder()
								   .setTotalPrice("10")
								   .setItemTotalPrice("10")
								   .setTax("0")
								   .setShippingPrice("0")
								   .setCurrencyCode("USD")
								   .build());

		PaymentManager mockedManager = mock(PaymentManager.class);

		stubPaymentManager(mockedManager);

		SamsungPay.startSamsungPay(mBraintreeFragment, info, new SamsungPayTransactionListener() {
			@Nullable
			@Override
			public PaymentInfo.Amount onAddressUpdated(@NotNull PaymentInfo paymentInfo) {
				return null;
			}

			@Nullable
			@Override
			public PaymentInfo.Amount onCardInfoUpdated(@NotNull CardInfo cardInfo) {
				return null;
			}
		});

		ArgumentCaptor<PaymentInfo> paymentInfoCaptor = ArgumentCaptor.forClass(PaymentInfo.class);
		verify(mockedManager).startInAppPay(paymentInfoCaptor.capture(), any(PaymentManager.TransactionInfoListener.class));

		PaymentInfo infoArgument = paymentInfoCaptor.getValue();
		assertEquals("sandbox_tmxhyf7d_dcpspy2brwdjr3qn", infoArgument.getMerchantId());
	}

	@Test
	public void startSamsungPay_setsMerchantName() {

	}

	@Test
	public void startSamsungPay_setsAllowedCardBrands() {

	}

	@Test
	public void startSamsungPay_whenAddressUpdated_callsListener() {

	}

	@Test
	public void startSamsungPay_whenCardInfoUpdated_callsListener() {

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

		Method getSamsungPay = SamsungPay.class.getDeclaredMethod("getSamsungPay", BraintreeFragment.class, BraintreeResponseListener.class);

		PowerMockito.replace(getSamsungPay).with(new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) {
				BraintreeResponseListener<com.samsung.android.sdk.samsungpay.v2.SamsungPay> listener =
						(BraintreeResponseListener<com.samsung.android.sdk.samsungpay.v2.SamsungPay>) args[1];

				listener.onResponse(mockSamsungPay);

				return null;
			}
		});
	}

	private void stubPaymentManager(final PaymentManager mockedPaymentManager) throws NoSuchMethodException {
		Method getPaymentManager = SamsungPay.class.getDeclaredMethod("getPaymentManager", BraintreeFragment.class, BraintreeResponseListener.class);

		PowerMockito.replace(getPaymentManager).with(new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) {
				BraintreeResponseListener<PaymentManager> listener = (BraintreeResponseListener) args[1];
				listener.onResponse(mockedPaymentManager);

				return null;
			}
		});
	}
}
