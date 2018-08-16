package com.braintreepayments.api;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.braintreepayments.api.exceptions.SamsungPayException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener;
import com.braintreepayments.api.internal.ClassHelper;
import com.braintreepayments.api.models.BraintreeRequestCodes;
import com.braintreepayments.api.models.SamsungPayNonce;
import com.braintreepayments.api.samsungpay.BuildConfig;
import com.samsung.android.sdk.samsungpay.v2.SpaySdk;
import com.samsung.android.sdk.samsungpay.v2.StatusListener;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AmountBoxControl;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AmountConstants;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet;
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
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.braintreepayments.api.models.BinData.*;
import static com.braintreepayments.api.test.FixturesHelper.stringFromFixture;
import static junit.framework.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(RobolectricTestRunner.class)
@PowerMockIgnore({"org.mockito.*", "org.robolectric.*", "android.*", "org.json.*"})
@PrepareForTest({
        ClassHelper.class,
        SamsungPay.class,
        com.samsung.android.sdk.samsungpay.v2.SamsungPay.class,
        PaymentManager.class
})
@Config(constants = BuildConfig.class)
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


    @Test
    public void isReadyToPay_whenSDKNotAvailable_returnsStatusNotSupported() throws InterruptedException {
        mockStatic(ClassHelper.class);
        when(ClassHelper.isClassAvailable(eq("com.samsung.android.sdk.samsungpay.v2.SamsungPay"))).thenReturn(false);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SpaySdk.SPAY_NOT_SUPPORTED, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSDKNotAvailable_sendsAnalyticEvent() throws InterruptedException {
        mockStatic(ClassHelper.class);
        when(ClassHelper.isClassAvailable(eq("com.samsung.android.sdk.samsungpay.v2.SamsungPay"))).thenReturn(false);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.samsung-pay-class-unavailable");
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsNotSupported_returnsStatusNotSupported() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_SUPPORTED);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SpaySdk.SPAY_NOT_SUPPORTED, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsNotSupported_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_SUPPORTED);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.device-not-supported");
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsNotReady_returnsStatusNotReady() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SpaySdk.SPAY_NOT_READY, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsNotReady_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.not-ready");
    }

    @Test
    public void isReadyToPay_whenSpayErrorReasonIsSetupNotCompleted_returnsErrorReasonSetupNotCompleted() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY, SpaySdk.ERROR_SPAY_SETUP_NOT_COMPLETED);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SpaySdk.ERROR_SPAY_SETUP_NOT_COMPLETED, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayErrorReasonIsSpayAppNeedToUpdate_returnsErrorReasonSpayAppNeedToUpdate() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY, SpaySdk.ERROR_SPAY_APP_NEED_TO_UPDATE);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SpaySdk.ERROR_SPAY_APP_NEED_TO_UPDATE, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsReady_returnsStatusReady() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        List<CardInfo> cardInfos = new ArrayList<>();
        cardInfos.add(new CardInfo.Builder().setBrand(SpaySdk.Brand.VISA).build());
        stubPaymentManagerRequestCardInfo(cardInfos);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SpaySdk.SPAY_READY, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsReady_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        List<CardInfo> cardInfos = new ArrayList<>();
        cardInfos.add(new CardInfo.Builder().setBrand(SpaySdk.Brand.VISA).build());
        stubPaymentManagerRequestCardInfo(cardInfos);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.ready");
    }

    @Test
    public void isReadyToPay_whenSpayHasNoSupportedCardBrands_returnsStatusNotReady() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(new ArrayList<CardInfo>());

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SpaySdk.SPAY_NOT_READY, availability.getStatus());
                assertEquals(SamsungPay.SPAY_NO_SUPPORTED_CARDS_IN_WALLET, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayHasNoSupportedCardBrands_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(new ArrayList<CardInfo>());

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.request-card-info.no-supported-cards-in-wallet");
    }

    @Test
    public void isReadyToPay_whenSpayReturnsNullForSupportedCardBrands_returnsStatusNotReady() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(null);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SpaySdk.SPAY_NOT_READY, availability.getStatus());
                assertEquals(SamsungPay.SPAY_NO_SUPPORTED_CARDS_IN_WALLET, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayReturnsNullForSupportedCardBrands_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(null);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.request-card-info.no-supported-cards-in-wallet");
    }

    @Test
    public void isReadyToPay_onFailure_postsExceptionAndReturnsAvailability() {
        com.samsung.android.sdk.samsungpay.v2.SamsungPay mockedSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                StatusListener listener = (StatusListener) invocation.getArguments()[0];

                listener.onFail(com.samsung.android.sdk.samsungpay.v2.SamsungPay.ERROR_DEVICE_NOT_SAMSUNG, new Bundle());

                return null;
            }
        }).when(mockedSamsungPay).getSamsungPayStatus(any(StatusListener.class));

        stubSamsungPay(mockedSamsungPay);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);

        verify(mBraintreeFragment).postCallback(exceptionCaptor.capture());

        Exception capturedException = exceptionCaptor.getValue();

        assertTrue(capturedException instanceof SamsungPayException);
        assertEquals(SpaySdk.ERROR_DEVICE_NOT_SAMSUNG, ((SamsungPayException) capturedException).getCode());
        assertNotNull(((SamsungPayException) capturedException).getExtras());
    }

    @Test
    public void isReadyToPay_onFailure_sendsAnalyticEvent() {
        com.samsung.android.sdk.samsungpay.v2.SamsungPay mockedSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                StatusListener listener = (StatusListener) invocation.getArguments()[0];

                listener.onFail(com.samsung.android.sdk.samsungpay.v2.SamsungPay.ERROR_DEVICE_NOT_SAMSUNG, new Bundle());

                return null;
            }
        }).when(mockedSamsungPay).getSamsungPayStatus(any(StatusListener.class));

        stubSamsungPay(mockedSamsungPay);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.failed");
    }

    @Test
    public void isReadyToPay_whenCardInfoRequestFails_postsError() {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(-1);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);

        verify(mBraintreeFragment).postCallback(exceptionCaptor.capture());

        Exception capturedException = exceptionCaptor.getValue();

        assertTrue(capturedException instanceof SamsungPayException);
        assertEquals(-1, ((SamsungPayException) capturedException).getCode());
        assertNotNull(((SamsungPayException) capturedException).getExtras());
    }

    @Test
    public void isReadyToPay_whenCardInfoRequestFails_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(-1);

        SamsungPay.isReadyToPay(mBraintreeFragment, this.<SamsungPayAvailability>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.request-card-info.failed");
    }

    @Test
    public void createPaymentInfo_setsMerchantValues() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.createPaymentInfo(mBraintreeFragment, new BraintreeResponseListener<CustomSheetPaymentInfo.Builder>() {
            @Override
            public void onResponse(CustomSheetPaymentInfo.Builder builder) {
                CustomSheetPaymentInfo paymentInfo = builder.build();
                assertEquals("example-samsung-authorization", paymentInfo.getMerchantId());
                assertEquals("some example merchant", paymentInfo.getMerchantName());

                List<SpaySdk.Brand> brands = paymentInfo.getAllowedCardBrands();
                assertTrue(brands.contains(SpaySdk.Brand.VISA));
                assertTrue(brands.contains(SpaySdk.Brand.DISCOVER));
                assertTrue(brands.contains(SpaySdk.Brand.MASTERCARD));
                assertTrue(brands.contains(SpaySdk.Brand.AMERICANEXPRESS));

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void createPaymentInfo_sendsAnalyticEvent() {
        SamsungPay.createPaymentInfo(mBraintreeFragment, this.<CustomSheetPaymentInfo.Builder>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.create-payment-info.success");
    }

    @Test
    public void createPaymentManager_sendsAnalyticEvent() {
        PaymentManager mockedManager = mock(PaymentManager.class);
        stubPaymentManager(mockedManager);
        SamsungPay.createPaymentManager(mBraintreeFragment, this.<PaymentManager>emptyResponse());

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.create-payment-manager.success");
    }

    @Test
    public void requestPayment_startsInAppPay() {
        PaymentManager mockedManager = mock(PaymentManager.class);
        stubPaymentManager(mockedManager);
        CustomSheetPaymentInfo paymentInfo = getCustomSheetPaymentInfo();

        SamsungPay.requestPayment(mBraintreeFragment, mockedManager, paymentInfo, mock(SamsungPayCustomTransactionUpdateListener.class));

        verify(mockedManager).startInAppPayWithCustomSheet(eq(paymentInfo), any(PaymentManager.CustomSheetTransactionInfoListener.class));
    }

    @Test
    public void requestPayment_onCardInfoUpdated_withNullCardInfo_doesNothing() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);
        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener);

        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onCardInfoUpdated(null, new CustomSheet());
        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_NO_NETWORK, null);

        verify(mockedListener, times(0)).onCardInfoUpdated(any(CardInfo.class), any(CustomSheet.class));
    }

    @Test
    public void requestPayment_onCardInfoUpdated_updatesPaymentManager() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        CustomSheet sheet = new CustomSheet();
        CardInfo info = new CardInfo.Builder().build();

        listenerCaptor.getValue().onCardInfoUpdated(info, sheet);
        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_NO_NETWORK, null);

        verify(mockedListener).onCardInfoUpdated(eq(info), eq(sheet));
        verify(mockedPaymentManager).updateSheet(eq(sheet));
    }

    @Test
    public void requestPayment_onFailure_postsException() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_NO_NETWORK, null);
        verify(mBraintreeFragment).postCallback(exceptionCaptor.capture());

        Exception capturedException = exceptionCaptor.getValue();

        assertTrue(capturedException instanceof SamsungPayException);
        assertEquals(SpaySdk.ERROR_NO_NETWORK, ((SamsungPayException) capturedException).getCode());
        assertNull(((SamsungPayException) capturedException).getExtras());
    }

    @Test
    public void requestPayment_onFailure_sendsAnalyticEvent() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_NO_NETWORK, null);

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.request-payment.failed");
    }

    @Test
    public void requestPayment_onFailureWhenUserCanceled_postsCancel() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        ArgumentCaptor<Integer> requestCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_USER_CANCELED, null);
        verify(mBraintreeFragment).postCancelCallback(requestCodeCaptor.capture());

        int capturedCode = requestCodeCaptor.getValue();

        assertEquals(BraintreeRequestCodes.SAMSUNG_PAY, capturedCode);
    }

    @Test
    public void requestPayment_onFailureWhenUserCanceled_sendsAnalyticEvent() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_USER_CANCELED, null);

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.request-payment.user-canceled");
    }

    @Test
    public void requestPayment_onSuccess_postsPaymentMethodNonce() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        ArgumentCaptor<SamsungPayNonce> paymentMethodNonceCaptor = ArgumentCaptor.forClass(SamsungPayNonce.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onSuccess(null, stringFromFixture("payment_methods/samsung_pay_response.json"), null);
        verify(mBraintreeFragment).postCallback(paymentMethodNonceCaptor.capture());

        SamsungPayNonce nonce = paymentMethodNonceCaptor.getValue();

        assertTrue(nonce instanceof SamsungPayNonce);
        assertEquals("Samsung Pay", nonce.getTypeLabel());
        assertEquals("tokensam_bf_v8s9hv_2htw4m_nh4f45_y3hsft_wty", nonce.getNonce());
        assertEquals("Mastercard", nonce.getCardType());
        assertEquals("1798", nonce.getSourceCardLast4());
        assertEquals("ending in 1798", nonce.getDescription());

        assertNotNull(nonce.getBinData());
        assertEquals(UNKNOWN, nonce.getBinData().getPrepaid());
        assertEquals(YES, nonce.getBinData().getHealthcare());
        assertEquals(NO, nonce.getBinData().getDebit());
        assertEquals(UNKNOWN, nonce.getBinData().getDurbinRegulated());
        assertEquals(UNKNOWN, nonce.getBinData().getCommercial());
        assertEquals(UNKNOWN, nonce.getBinData().getPayroll());
        assertEquals(UNKNOWN, nonce.getBinData().getIssuingBank());
        assertEquals("US", nonce.getBinData().getCountryOfIssuance());
        assertEquals("123", nonce.getBinData().getProductId());
    }

    @Test
    public void requestPayment_onSuccess_sendsAnalyticEvent() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onSuccess(null, stringFromFixture("payment_methods/samsung_pay_response.json"), null);

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.request-payment.success");
    }

    @Test
    public void goToUpdatePage_callsGoToUpdatePage() {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay.goToUpdatePage(mBraintreeFragment);

        verify(mockSamsungPay).goToUpdatePage();
    }

    @Test
    public void goToUpdatePage_sendsAnalyticEvent() {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay.goToUpdatePage(mBraintreeFragment);

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.goto-update-page");
    }

    @Test
    public void activateSamsungPay_callsActivateSamsungPay() {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay.activateSamsungPay(mBraintreeFragment);

        verify(mockSamsungPay).activateSamsungPay();
    }

    @Test
    public void activateSamsungPay_sendsAnalyticEvent() {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay.activateSamsungPay(mBraintreeFragment);

        verify(mBraintreeFragment).sendAnalyticsEvent("samsung-pay.activate-samsung-pay");
    }

    private void stubSamsungPayStatus(final int status) {
        stubSamsungPayStatus(status, -10000);
    }

    private void stubSamsungPayStatus(final int status, final int reason) {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockedSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);

        PowerMockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                StatusListener listener = (StatusListener) invocation.getArguments()[0];
                Bundle bundle = new Bundle();
                if (reason != -10000) {
                    bundle.putInt(SpaySdk.EXTRA_ERROR_REASON, reason);
                }

                listener.onSuccess(status, bundle);

                return null;
            }
        }).when(mockedSamsungPay).getSamsungPayStatus(any(StatusListener.class));

        stubSamsungPay(mockedSamsungPay);
    }

    private void stubSamsungPay(final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockedSamsungPay) {
        stub(method(SamsungPay.Companion.class, "getSamsungPay")).toReturn(mockedSamsungPay);
    }

    private void stubPaymentManager(final PaymentManager mockedPaymentManager) {
        stub(method(SamsungPay.Companion.class, "getPaymentManager")).toReturn(mockedPaymentManager);
    }

    private void stubPaymentManagerRequestCardInfo(final List<CardInfo> cardInfos) {
        final PaymentManager mockedPaymentManager = mock(PaymentManager.class);
        PowerMockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                PaymentManager.CardInfoListener listener = (PaymentManager.CardInfoListener) invocation.getArguments()[1];
                listener.onResult(cardInfos);
                return null;
            }
        }).when(mockedPaymentManager).requestCardInfo(any(Bundle.class), any(PaymentManager.CardInfoListener.class));

        stubPaymentManager(mockedPaymentManager);
    }

    private void stubPaymentManagerRequestCardInfo(final int errorCode) {
        final PaymentManager mockedPaymentManager = mock(PaymentManager.class);
        PowerMockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                PaymentManager.CardInfoListener listener = (PaymentManager.CardInfoListener) invocation.getArguments()[1];
                listener.onFailure(errorCode, Bundle.EMPTY);
                return null;
            }
        }).when(mockedPaymentManager).requestCardInfo(any(Bundle.class), any(PaymentManager.CardInfoListener.class));

        stubPaymentManager(mockedPaymentManager);
    }

    private CustomSheetPaymentInfo getCustomSheetPaymentInfo() {
        final AmountBoxControl amountBoxControl = new AmountBoxControl("amountID", "USD");
        amountBoxControl.addItem("itemId", "Items", 1000, "");
        amountBoxControl.addItem("taxId", "Tax", 50, "");
        amountBoxControl.addItem("shippingId", "Shipping", 10, "");
        amountBoxControl.addItem("interestId", "Interest [ex]", 0, "");
        amountBoxControl.setAmountTotal(1050 + amountBoxControl.getValue("shippingId") + amountBoxControl.getValue("interestId"), AmountConstants.FORMAT_TOTAL_PRICE_ONLY);
        amountBoxControl.addItem(3, "fuelId", "FUEL", 0, "Pending");

        CustomSheet sheet = new CustomSheet();
        sheet.addControl(amountBoxControl);

        return new CustomSheetPaymentInfo.Builder()
                .setCustomSheet(sheet)
                .build();
    }

    private <T> BraintreeResponseListener<T> emptyResponse() {
        return emptyResponse(null);
    }

    private <T> BraintreeResponseListener<T> emptyResponse(final CountDownLatch latch) {
        return new BraintreeResponseListener<T>() {
            @Override
            public void onResponse(T t) {
                if (latch != null) {
                    latch.countDown();
                }

            }
        };
    }
}
