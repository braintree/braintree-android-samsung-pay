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
import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.models.SamsungPayNonce;
import com.braintreepayments.api.test.TestConfigurationBuilder;
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo;
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
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.braintreepayments.api.models.BinData.NO;
import static com.braintreepayments.api.models.BinData.UNKNOWN;
import static com.braintreepayments.api.models.BinData.YES;
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

@RunWith(RobolectricTestRunner.class)
@PowerMockIgnore({"org.mockito.*", "org.robolectric.*", "android.*", "org.json.*", "com.samsung.*"})
@PrepareForTest(value = {
        ClassHelper.class,
        SamsungPay.class,
        com.samsung.android.sdk.samsungpay.v2.SamsungPay.class,
        PaymentManager.class
},
        fullyQualifiedNames = {
                "com.samsung.android.sdk.samsungpay.v2.SpaySdk$Brand$1"
        }
)
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
    public void isReadyToPay_whenSDKNotAvailable_returnsStatusNotSupported() {
        mockStatic(ClassHelper.class);
        when(ClassHelper.isClassAvailable(eq("com.samsung.android.sdk.samsungpay.v2.SamsungPay"))).thenReturn(false);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayStatus.NOT_SUPPORTED, availability.getStatus());

                latch.countDown();
            }
        });
    }

    @Test(timeout = 1000)
    public void isReadyToPay_whenSpayStatusIsNotSupported_returnsStatusNotSupported() throws Exception {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_SUPPORTED);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayStatus.NOT_SUPPORTED, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test(timeout = 1000)
    public void isReadyToPay_whenSpayStatusIsNotReady_returnsStatusNotReady() throws NoSuchMethodException, InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayStatus.NOT_READY, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayErrorReasonIsSetupNotCompleted_returnsErrorReasonSetupNotCompleted() throws NoSuchMethodException, InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY, SpaySdk.ERROR_SPAY_SETUP_NOT_COMPLETED);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayErrorReason.SETUP_NOT_COMPLETED, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayErrorReasonIsSpayAppNeedToUpdate_returnsErrorReasonSpayAppNeedToUpdate() throws NoSuchMethodException, InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY, SpaySdk.ERROR_SPAY_APP_NEED_TO_UPDATE);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayErrorReason.NEED_TO_UPDATE_SPAY_APP, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayErrorReasonNotGiven_returnsErrorReasonUnknown() throws NoSuchMethodException, InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_NOT_READY);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayErrorReason.UNKNOWN, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayStatusIsReady_returnsStatusReady() throws NoSuchMethodException, InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        List<CardInfo> cardInfos = new ArrayList<CardInfo>();
        cardInfos.add(new CardInfo.Builder().setBrand(SpaySdk.Brand.VISA).build());
        stubPaymentManagerRequestCardInfo(cardInfos);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayStatus.READY, availability.getStatus());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayHasNoSupportedCardBrands_returnsStatusNotReady() throws NoSuchMethodException, InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(new ArrayList<CardInfo>());

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayStatus.NOT_READY, availability.getStatus());
                assertEquals(SamsungPayErrorReason.NO_SUPPORTED_CARDS_IN_WALLET, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }

    @Test
    public void isReadyToPay_whenSpayReturnsNullForSupportedCardBrands_returnsStatusNotReady() throws NoSuchMethodException, InterruptedException {
        stubSamsungPayStatus(SpaySdk.SPAY_READY);
        stubPaymentManagerRequestCardInfo(null);

        final CountDownLatch latch = new CountDownLatch(1);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayStatus.NOT_READY, availability.getStatus());
                assertEquals(SamsungPayErrorReason.NO_SUPPORTED_CARDS_IN_WALLET, availability.getReason());

                latch.countDown();
            }
        });

        latch.await();
    }


    @Test
    public void isReadyToPay_onFailure_postsExceptionAndReturnsAvailability() throws InterruptedException, NoSuchMethodException {
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

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                assertEquals(SamsungPayStatus.ERROR, availability.getStatus());
                // TODO: should we create a SamsungPayErrorReason? Or should availability contain the exception? Or is UNKNOWN ok?
                latch.countDown();
            }
        });

        latch.await();

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);

        verify(mBraintreeFragment).postCallback(exceptionCaptor.capture());

        Exception capturedException = exceptionCaptor.getValue();

        assertTrue(capturedException instanceof SamsungPayException);
        assertEquals(SpaySdk.ERROR_DEVICE_NOT_SAMSUNG, ((SamsungPayException) capturedException).getCode());
        assertNotNull(((SamsungPayException) capturedException).getExtras());
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
    public void requestPayment_startsInAppPay() throws NoSuchMethodException {
        PaymentManager mockedManager = mock(PaymentManager.class);
        stubPaymentManager(mockedManager);
        CustomSheetPaymentInfo paymentInfo = getCustomSheetPaymentInfo();

        SamsungPay.requestPayment(mBraintreeFragment, paymentInfo, mock(SamsungPayCustomTransactionUpdateListener.class));

        verify(mockedManager).startInAppPayWithCustomSheet(eq(paymentInfo), any(PaymentManager.CustomSheetTransactionInfoListener.class));
    }

//    @Test
//    public void requestPayment_usesPaymentManagerWithServiceIdFromConfiguration() throws NoSuchMethodException {
//        Configuration configuration = new TestConfigurationBuilder()
//                .samsungPay(new TestConfigurationBuilder.TestSamsungPayConfigurationBuilder()
//                            .serviceId("service-id")
//                            )
//                .buildConfiguration();
//        MockFragmentBuilder fragmentBuilder = new MockFragmentBuilder()
//                .configuration(configuration);
//        PaymentManager mockedPaymentManager = mock(PaymentManager.class);
//        PowerMockito.doNothing().when(mockedPaymentManager)
//                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
//                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
//        stub(method(SamsungPay.class, "getPaymentManager")).toReturn(mockedPaymentManager);
//        ArgumentCaptor<BraintreePartnerInfo> infoCaptor = ArgumentCaptor.forClass(BraintreePartnerInfo.class);
//
//        SamsungPay.requestPayment(fragmentBuilder.build(), getCustomSheetPaymentInfo(), mock(SamsungPayCustomTransactionUpdateListener.class));
//
//        assertEquals("service-id", mockedPaymentManager);
//    }
//
//    @Test
//    public void requestPayment_usesPaymentManagerWithInAppPartnerServiceType() throws Exception {
//    }
//
//    @Test
//    public void requestPayment_usesPaymentManagerWithClientSdkMetadata() {
//        // TODO
//    }
//
    @Test
    public void requestPayment_onCardInfoUpdated_withNullCardInfo_doesNothing() throws NoSuchMethodException {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);
        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, getCustomSheetPaymentInfo(), mockedListener);

        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onCardInfoUpdated(null, new CustomSheet());
        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_NO_NETWORK, null);

        verify(mockedListener, times(0)).onCardInfoUpdated(any(CardInfo.class), any(CustomSheet.class));
    }

    @Test
    public void requestPayment_onCardInfoUpdated_updatesPaymentManager() throws NoSuchMethodException {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, getCustomSheetPaymentInfo(), mockedListener);
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
    public void requestPayment_onFailure_postsException() throws NoSuchMethodException {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, getCustomSheetPaymentInfo(), mockedListener);
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
    public void requestPayment_onSuccess_postsPaymentMethodNonce() throws NoSuchMethodException {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        ArgumentCaptor<SamsungPayNonce> paymentMethodNonceCaptor = ArgumentCaptor.forClass(SamsungPayNonce.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, getCustomSheetPaymentInfo(), mockedListener);
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
    public void goToUpdatePage_callsGoToUpdatePage() throws NoSuchMethodException {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay.goToUpdatePage(mBraintreeFragment);

        verify(mockSamsungPay).goToUpdatePage();
    }

    @Test
    public void activateSamsungPay_callsActivateSamsungPay() throws NoSuchMethodException {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);
        stubSamsungPay(mockSamsungPay);

        SamsungPay.activateSamsungPay(mBraintreeFragment);

        verify(mockSamsungPay).activateSamsungPay();
    }

    private void stubSamsungPayStatus(final int status) throws NoSuchMethodException {
        stubSamsungPayStatus(status, -10000);
    }

    private void stubSamsungPayStatus(final int status, final int reason) throws NoSuchMethodException {
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

    private void stubSamsungPay(final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockedSamsungPay) throws NoSuchMethodException {
        Method getSamsungPay = SamsungPay.class.getDeclaredMethod("getSamsungPay", BraintreeFragment.class, PartnerInfo.class);

        PowerMockito.replace(getSamsungPay).with(new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return mockedSamsungPay;
            }
        });
    }

    private void stubPaymentManager(final PaymentManager mockedPaymentManager) throws NoSuchMethodException {
        stub(method(SamsungPay.class, "getPaymentManager")).toReturn(mockedPaymentManager);
    }

    private void stubPaymentManagerRequestCardInfo(final List<CardInfo> cardInfos) throws NoSuchMethodException {
        final PaymentManager mockedPaymentManager = mock(PaymentManager.class);
        PowerMockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                PaymentManager.CardInfoListener listener = (PaymentManager.CardInfoListener) invocation.getArguments()[1];
                listener.onResult(cardInfos);
                return null;
            }
        }).when(mockedPaymentManager).requestCardInfo(any(Bundle.class), any(PaymentManager.CardInfoListener.class));
        Method getPaymentManager = SamsungPay.class.getDeclaredMethod("getPaymentManager", BraintreeFragment.class, PartnerInfo.class);
        PowerMockito.replace(getPaymentManager).with(new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return mockedPaymentManager;
            }
        });
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
}
