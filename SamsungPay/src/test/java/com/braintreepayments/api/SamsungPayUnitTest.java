package com.braintreepayments.api;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.braintreepayments.api.exceptions.SamsungPayException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener;
import com.braintreepayments.api.interfaces.SamsungPayTransactionUpdateListener;
import com.braintreepayments.api.internal.ClassHelper;
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo;
import com.samsung.android.sdk.samsungpay.v2.SpaySdk;
import com.samsung.android.sdk.samsungpay.v2.StatusListener;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
    public void isReadyToPay_onFailure_postsException() throws InterruptedException, NoSuchMethodException {
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
    public void startSamsungPay_setsMerchantValues() throws NoSuchMethodException {
        CustomSheetPaymentInfo.Builder customSheetPaymentInfoBuilder = getCustomSheetPaymentInfoBuilder();

        PaymentManager mockedManager = mock(PaymentManager.class);

        stubPaymentManager(mockedManager);

        SamsungPay.requestPayment(mBraintreeFragment, customSheetPaymentInfoBuilder, mock(SamsungPayCustomTransactionUpdateListener.class));

        ArgumentCaptor<CustomSheetPaymentInfo> customSheetInfoCaptor = ArgumentCaptor.forClass(CustomSheetPaymentInfo.class);

        verify(mockedManager).startInAppPayWithCustomSheet(customSheetInfoCaptor.capture(), any(PaymentManager.CustomSheetTransactionInfoListener.class));

        CustomSheetPaymentInfo infoArgument = customSheetInfoCaptor.getValue();

        assertEquals("example-samsung-authorization", infoArgument.getMerchantId());
        assertEquals("some example merchant", infoArgument.getMerchantName());

        List<SpaySdk.Brand> brands = infoArgument.getAllowedCardBrands();
        assertTrue(brands.contains(SpaySdk.Brand.VISA));
        assertTrue(brands.contains(SpaySdk.Brand.DISCOVER));
        assertTrue(brands.contains(SpaySdk.Brand.MASTERCARD));
        assertTrue(brands.contains(SpaySdk.Brand.AMERICANEXPRESS));
    }

    @Test
    public void requestPayment_onCardInfoUpdated_withNullCardInfo_doesNothing() throws NoSuchMethodException {
        PaymentManager mockedPaymentManager = mock(PaymentManager.class);

        PowerMockito.doNothing().when(mockedPaymentManager)
                .startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                        any(PaymentManager.CustomSheetTransactionInfoListener.class));
        stubPaymentManager(mockedPaymentManager);

        ArgumentCaptor<PaymentManager.CustomSheetTransactionInfoListener> listenerCaptor = ArgumentCaptor.forClass(PaymentManager.CustomSheetTransactionInfoListener.class);
        SamsungPayCustomTransactionUpdateListener mockedListener = mock(SamsungPayCustomTransactionUpdateListener.class);

        SamsungPay.requestPayment(mBraintreeFragment, getCustomSheetPaymentInfoBuilder(), mockedListener);
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

        SamsungPay.requestPayment(mBraintreeFragment, getCustomSheetPaymentInfoBuilder(), mockedListener);
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

        SamsungPay.requestPayment(mBraintreeFragment, getCustomSheetPaymentInfoBuilder(), mockedListener);
        verify(mockedPaymentManager).startInAppPayWithCustomSheet(any(CustomSheetPaymentInfo.class),
                listenerCaptor.capture());

        listenerCaptor.getValue().onFailure(SpaySdk.ERROR_NO_NETWORK, null);
        verify(mBraintreeFragment).postCallback(exceptionCaptor.capture());

        Exception capturedException = exceptionCaptor.getValue();

        assertTrue(capturedException instanceof SamsungPayException);
        assertEquals(SpaySdk.ERROR_NO_NETWORK, ((SamsungPayException) capturedException).getCode());
        assertNull(((SamsungPayException) capturedException).getExtras());
    }

    private void stubSamsungPayStatus(final int status) throws NoSuchMethodException {
        final com.samsung.android.sdk.samsungpay.v2.SamsungPay mockedSamsungPay = mock(com.samsung.android.sdk.samsungpay.v2.SamsungPay.class);

        PowerMockito.doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                StatusListener listener = (StatusListener) invocation.getArguments()[0];

                listener.onSuccess(status, new Bundle());

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
        Method getPaymentManager = SamsungPay.class.getDeclaredMethod("getPaymentManager", BraintreeFragment.class, PartnerInfo.class);

        PowerMockito.replace(getPaymentManager).with(new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return mockedPaymentManager;
            }
        });
    }

    private CustomSheetPaymentInfo.Builder getCustomSheetPaymentInfoBuilder() {
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
                .setCustomSheet(sheet);
    }
}
