package com.braintreepayments.api;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.samsung.android.sdk.samsungpay.v2.PartnerInfo;
import com.samsung.android.sdk.samsungpay.v2.SpaySdk;
import com.samsung.android.sdk.samsungpay.v2.StatusListener;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AmountBoxControl;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AmountConstants;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet;

import org.json.JSONObject;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.braintreepayments.api.BinData.NO;
import static com.braintreepayments.api.BinData.UNKNOWN;
import static com.braintreepayments.api.BinData.YES;
import static com.braintreepayments.api.test.FixturesHelper.stringFromFixture;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.method;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.stub;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(RobolectricTestRunner.class)
@PowerMockIgnore({"org.powermock.*", "org.mockito.*", "org.robolectric.*", "android.*", "org.json.*"})
@PrepareForTest({
        ClassHelper.class,
        SamsungPay.class,
        com.samsung.android.sdk.samsungpay.v2.SamsungPay.class,
        PaymentManager.class
})
public class SamsungPayUnitTest {
    private static final String BRAINTREE_TOKENIZATION_API_VERSION = "2018-10-01";

    @Rule
    public PowerMockRule mPowerMockRule = new PowerMockRule();

    private BraintreeClient braintreeClient;
    private Context context;
    private SamsungPayTransactionCallback samsungPayTransactionCallback;
    private SamsungPayIsReadyToPayCallback samsungPayIsReadyToPayCallback;
    private SamsungPayCreatePaymentManagerCallback samsungPayCreatePaymentManagerCallback;
    private SamsungPayCreatePaymentInfoCallback samsungPayCreatePaymentInfoCallback;

    @Before
    public void setup() throws Exception {
        braintreeClient = new MockBraintreeClientBuilder()
                .configuration(Configuration.fromJson(stringFromFixture("configuration/with_samsung_pay.json")))
                .build();

        context = mock(Context.class);
        samsungPayTransactionCallback = mock(SamsungPayTransactionCallback.class);
        samsungPayIsReadyToPayCallback = mock(SamsungPayIsReadyToPayCallback.class);
        samsungPayCreatePaymentManagerCallback = mock(SamsungPayCreatePaymentManagerCallback.class);
        samsungPayCreatePaymentInfoCallback = mock(SamsungPayCreatePaymentInfoCallback.class);
        ApplicationInfo mockApplicationInfo = mock(ApplicationInfo.class);

        Bundle mockBundle = mock(Bundle.class);
        when(mockBundle.getFloat(eq("spay_sdk_api_level"))).thenReturn(1.9f);
        mockApplicationInfo.metaData = mockBundle;

        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mockPackageManager.getApplicationInfo(anyString(), anyInt())).thenReturn(mockApplicationInfo);

//        Context spiedContext = spy(RuntimeEnvironment.application);
//        when(spiedContext.getPackageManager()).thenReturn(mockPackageManager);
//        when(mBraintreeFragment.getApplicationContext()).thenReturn(spiedContext);
    }


    @Test
    public void isReadyToPay_whenSDKNotAvailable_returnsStatusNotSupported() throws InterruptedException {
        ClassHelper classHelper = mock(ClassHelper.class);
        when(classHelper.isClassAvailable(eq("com.samsung.android.sdk.samsungpay.v2.SamsungPay"))).thenReturn(false);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
                assertEquals(SpaySdk.SPAY_NOT_SUPPORTED, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSDKNotAvailable_sendsAnalyticEvent() throws InterruptedException {
        ClassHelper classHelper = mock(ClassHelper.class);
        when(classHelper.isClassAvailable(eq("com.samsung.android.sdk.samsungpay.v2.SamsungPay"))).thenReturn(false);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, samsungPayIsReadyToPayCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.samsung-pay-class-unavailable");
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsNotSupported_returnsStatusNotSupported() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_SUPPORTED);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
                assertEquals(SpaySdk.SPAY_NOT_SUPPORTED, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsNotSupported_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_SUPPORTED);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, samsungPayIsReadyToPayCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.device-not-supported");
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsNotReady_returnsStatusNotReady() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
                assertEquals(SpaySdk.SPAY_NOT_READY, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsNotReady_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, samsungPayIsReadyToPayCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.not-ready");
    }

    @Test
    public void isReadyToPay_whenSpayErrorReasonIsSetupNotCompleted_returnsErrorReasonSetupNotCompleted() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY, SpaySdk.ERROR_SPAY_SETUP_NOT_COMPLETED);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, samsungPayIsReadyToPayCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.ready");
    }

    @Test
    public void isReadyToPay_whenSpayHasNoSupportedCardBrands_returnsStatusNotReady() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(new ArrayList<CardInfo>());

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, samsungPayIsReadyToPayCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.request-card-info.no-supported-cards-in-wallet");
    }

    @Test
    public void isReadyToPay_whenSpayReturnsNullForSupportedCardBrands_returnsStatusNotReady() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(null);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, samsungPayIsReadyToPayCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.request-card-info.no-supported-cards-in-wallet");
    }

    @Test
    public void isReadyToPay_onFailure_postsExceptionAndReturnsAvailability() throws InterruptedException {
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

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
                assertTrue(error instanceof SamsungPayException);
                assertEquals(SpaySdk.ERROR_DEVICE_NOT_SAMSUNG, ((SamsungPayException) error).getCode());
                assertNotNull(((SamsungPayException) error).getExtras());

                latch.countDown();
            }
        });

        latch.await();
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, samsungPayIsReadyToPayCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.is-ready-to-pay.failed");
    }

    @Test
    public void isReadyToPay_whenCardInfoRequestFails_postsError() throws InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(-1);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, new SamsungPayIsReadyToPayCallback() {
            @Override
            public void onResult(SamsungPayAvailability availability, Exception error) {
                assertTrue(error instanceof SamsungPayException);
                assertEquals(-1, ((SamsungPayException) error).getCode());
                assertNotNull(((SamsungPayException) error).getExtras());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenCardInfoRequestFails_sendsAnalyticEvent() {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(-1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.isReadyToPay(context, samsungPayIsReadyToPayCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.request-card-info.failed");
    }

    @Test
    public void createPaymentInfo_setsMerchantValues() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.createPaymentInfo(new SamsungPayCreatePaymentInfoCallback() {
            @Override
            public void onResult(CustomSheetPaymentInfo.Builder builder, Exception error) {
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
        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.createPaymentInfo(samsungPayCreatePaymentInfoCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.create-payment-info.success");
    }

    @Test
    public void createPaymentManager_setsBraintreeDataForTokenization() throws Exception {
//        mockStatic(PaymentManager.class);
//        PaymentManager paymentManager = mock(PaymentManager.class);
//        whenNew(PaymentManager.class).withAnyArguments().thenReturn(paymentManager);
        PaymentManager mockedManager = mock(PaymentManager.class);
        stubPaymentManager(mockedManager);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.createPaymentManager(context, samsungPayCreatePaymentManagerCallback);

        ArgumentCaptor<PartnerInfo> argumentCaptor = ArgumentCaptor.forClass(PartnerInfo.class);
        verifyNew(PaymentManager.class).withArguments(any(), argumentCaptor.capture());

        Bundle partnerData = argumentCaptor.getValue().getData();

        assertEquals(SpaySdk.ServiceType.INAPP_PAYMENT.toString(), partnerData.getString(SpaySdk.PARTNER_SERVICE_TYPE));
        assertTrue(partnerData.getBoolean(PaymentManager.EXTRA_KEY_TEST_MODE));

        JSONObject additionalData = new JSONObject(partnerData.getString("additionalData"));

        String clientSdkMetadata = new MetadataBuilder()
                .integration(braintreeClient.getIntegrationType())
                .sessionId(braintreeClient.getSessionId())
                .version()
                .build()
                .toString();

        assertEquals(clientSdkMetadata, additionalData.getString("clientSdkMetadata"));
        assertEquals(BRAINTREE_TOKENIZATION_API_VERSION, additionalData.getString("braintreeTokenizationApiVersion"));
    }

    @Test
    public void createPaymentManager_sendsAnalyticEvent() {
        PaymentManager mockedManager = mock(PaymentManager.class);
        stubPaymentManager(mockedManager);
        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.createPaymentManager(context, samsungPayCreatePaymentManagerCallback);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.create-payment-manager.success");
    }

    @Test
    public void requestPayment_startsInAppPay() {
        PaymentManager mockedManager = mock(PaymentManager.class);
        stubPaymentManager(mockedManager);
        CustomSheetPaymentInfo paymentInfo = getCustomSheetPaymentInfo();

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedManager, paymentInfo, mock(SamsungPayCustomTransactionUpdateListener.class), samsungPayTransactionCallback);

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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener, samsungPayTransactionCallback);

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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener, samsungPayTransactionCallback);
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener, samsungPayTransactionCallback);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_NO_NETWORK, null);
        verify(samsungPayTransactionCallback).onResult((SamsungPayNonce) isNull(), exceptionCaptor.capture());

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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener, samsungPayTransactionCallback);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_NO_NETWORK, null);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.request-payment.failed");
    }

    @Test
    public void requestPayment_onFailureWhenUserCanceled_postsCancel() {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
//        ArgumentCaptor<Integer> requestCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener, samsungPayTransactionCallback);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_USER_CANCELED, null);
        verify(samsungPayTransactionCallback).onResult(null, null);
//        verify(samsungPayTransactionCallback).onResult(requestCodeCaptor.capture(), null);
//
//        int capturedCode = requestCodeCaptor.getValue();
//
//        assertEquals(BraintreeRequestCodes.SAMSUNG_PAY, capturedCode);
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener, samsungPayTransactionCallback);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_USER_CANCELED, null);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.request-payment.user-canceled");
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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener, samsungPayTransactionCallback);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onSuccess(null, stringFromFixture("payment_methods/samsung_pay_response.json"), null);
        verify(samsungPayTransactionCallback).onResult(paymentMethodNonceCaptor.capture(), (Exception) isNull());

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

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.requestPayment(mockedPaymentManager, getCustomSheetPaymentInfo(), mockedListener, samsungPayTransactionCallback);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onSuccess(null, stringFromFixture("payment_methods/samsung_pay_response.json"), null);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.request-payment.success");
    }

    @Test
    public void goToUpdatePage_callsGoToUpdatePage() {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.goToUpdatePage(context);

        verify(mockSamsungPay).goToUpdatePage();
    }

    @Test
    public void goToUpdatePage_sendsAnalyticEvent() {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.goToUpdatePage(context);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.goto-update-page");
    }

    @Test
    public void activateSamsungPay_callsActivateSamsungPay() {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.activateSamsungPay(context);

        verify(mockSamsungPay).activateSamsungPay();
    }

    @Test
    public void activateSamsungPay_sendsAnalyticEvent() {
        SamsungPay sut = new SamsungPay(braintreeClient);
        sut.activateSamsungPay(context);

        verify(braintreeClient).sendAnalyticsEvent("samsung-pay.activate-samsung-pay");
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
        stub(method(SamsungPay.class, "getSamsungPay")).toReturn(mockedSamsungPay);
    }

    private void stubPaymentManager(final com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager mockedPaymentManager) {
        stub(method(SamsungPay.class, "getPaymentManager")).toReturn(mockedPaymentManager);
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
