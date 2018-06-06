@file:JvmName("SamsungPay")

package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.exceptions.SamsungPayException
import com.braintreepayments.api.interfaces.BraintreeErrorListener
import com.braintreepayments.api.interfaces.BraintreeResponseListener
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener
import com.braintreepayments.api.internal.ClassHelper
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.SamsungPay
import com.samsung.android.sdk.samsungpay.v2.SpaySdk
import com.samsung.android.sdk.samsungpay.v2.SpaySdk.*
import com.samsung.android.sdk.samsungpay.v2.StatusListener
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import java.util.*

/**
 * Call isReadyToPay before starting your Samsung Pay flow. isReadyToPay will call you back with the
 * status of Samsung Pay. If the Samsung Pay jar has not been included, or if the status of
 * Samsung Pay is anything but [SamsungPay.SPAY_READY], the listener will be called back
 * with a value of false. If the Samsung Pay callback fails and returns an error, that error
 * will be posted to the [BraintreeErrorListener] callback attached to the instance of
 * [BraintreeFragment] passed in here.
 *
 * TODO(Modify this s.t. the response listener also passes the reason for readiness, so that the merchant can take available actions)
 *
 * @param [fragment] TODO
 * @param [listener] TODO
 */
fun isReadyToPay(fragment: BraintreeFragment, listener: BraintreeResponseListener<Boolean>) {
    if (!isSamsungPayAvailable()) {
        listener.onResponse(false)
        return
    }

    getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
        val samsungPay = getSamsungPay(fragment, braintreePartnerInfo)

        samsungPay.getSamsungPayStatus(object : StatusListener {
            override fun onSuccess(status: Int, bundle: Bundle) {
                when (status) {
                    SPAY_NOT_SUPPORTED,
                    SPAY_NOT_READY -> listener.onResponse(false)
                    SPAY_READY -> listener.onResponse(true)
                }
            }

            override fun onFail(errorCode: Int, bundle: Bundle) {
                listener.onResponse(false)
                fragment.postCallback(SamsungPayException(errorCode, bundle))
            }
        })
    })
}

/**
 * Creates a {@link CustomSheetPaymentInfo.Builder} with the merchant ID, merchant name, and allowed
 * card brands populated by the Braintree merchant account configuration.
 * @param fragment - {@link BraintreeFragment}
 * @param listener {@link BraintreeResponseListener<CustomSheetPaymentInfo.Builder>} - listens for
 * the Braintree flavored {@link CustomSheetPaymentInfo.Builder}.
 */
fun createPaymentInfo(
    fragment: BraintreeFragment,
    listener: BraintreeResponseListener<CustomSheetPaymentInfo.Builder>
) {
    getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
        var paymentInfo = CustomSheetPaymentInfo.Builder()
                .setMerchantId(braintreePartnerInfo.configuration.samsungAuthorization)
                .setMerchantName(braintreePartnerInfo.configuration.merchantDisplayName)
                .setAllowedCardBrands(getAcceptedCardBrands(braintreePartnerInfo.configuration.supportedCardBrands))
        listener.onResponse(paymentInfo)
    })
}

/**
 * [requestPayment] takes a CustomSheetInfo.Builder and starts intitiates the Samsung Pay flow
 * with some custom UI provided by you.
 *
 * @param [fragment] TODO
 * @param [customSheetPaymentInfoBuilder] TODO
 * @param [listener] TODO
 */
fun requestPayment(
    fragment: BraintreeFragment,
    customSheetPaymentInfo: CustomSheetPaymentInfo,
    listener: SamsungPayCustomTransactionUpdateListener
) {
    getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
        val paymentManager = getPaymentManager(fragment, braintreePartnerInfo)

        paymentManager.startInAppPayWithCustomSheet(
            customSheetPaymentInfo,
            SamsungPayCustomTransactionListenerWrapper(fragment, paymentManager, listener)
        )
    })
}

/**
 * @return true if the SamsungPay SDK is available in the classpath, i.e. you have included
 * the Samsung Pay jar file in your declared app dependencies.
 */
fun isSamsungPayAvailable(): Boolean {
    return ClassHelper.isClassAvailable("com.samsung.android.sdk.samsungpay.v2.SamsungPay")
}

private fun getAcceptedCardBrands(configurationBrands: Set<String>): List<SpaySdk.Brand> {
    val samsungAcceptedList = ArrayList<SpaySdk.Brand>()

    for (braintreeAcceptedCardBrand in configurationBrands) {
        samsungAcceptedList.add(
            when (braintreeAcceptedCardBrand.toLowerCase()) {
                "visa" -> SpaySdk.Brand.VISA
                "mastercard" -> SpaySdk.Brand.MASTERCARD
                "discover" -> SpaySdk.Brand.DISCOVER
                "american_express" -> SpaySdk.Brand.AMERICANEXPRESS
                else -> SpaySdk.Brand.UNKNOWN_CARD
            }
        )
    }

    return samsungAcceptedList
}

internal fun getPartnerInfo(fragment: BraintreeFragment, listener: BraintreeResponseListener<BraintreePartnerInfo>) {
    fragment.waitForConfiguration { configuration ->
        val samsungPayConfiguration = configuration.samsungPay

        val bundle = Bundle()
        bundle.putString(SamsungPay.PARTNER_SERVICE_TYPE, SpaySdk.ServiceType.INAPP_PAYMENT.toString())

        listener.onResponse(BraintreePartnerInfo(samsungPayConfiguration, bundle))
    }
}

internal fun getSamsungPay(fragment: BraintreeFragment, info: PartnerInfo): SamsungPay {
    return SamsungPay(fragment.applicationContext, info)
}

internal fun getPaymentManager(fragment: BraintreeFragment, info: PartnerInfo): PaymentManager {
    return PaymentManager(fragment.applicationContext, info)
}
