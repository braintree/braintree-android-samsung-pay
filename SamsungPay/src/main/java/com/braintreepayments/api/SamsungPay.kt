@file:JvmName("SamsungPay")

package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.exceptions.ConfigurationException
import com.braintreepayments.api.interfaces.BraintreeResponseListener
import com.braintreepayments.api.internal.ClassHelper
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.SamsungPay
import com.samsung.android.sdk.samsungpay.v2.SpaySdk
import com.samsung.android.sdk.samsungpay.v2.StatusListener
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import java.util.*

// TODO pull SamsungPay stanza out of out of configuration, look for these values
private val serviceId = "1f7bb987065d4e16aa5f1f"
private val merchantId = "sandbox_tmxhyf7d_dcpspy2brwdjr3qn" // TODO pull merchantId out of configuration
private val merchantName = "bt-dx-integration-test"

/**
 * Call isReadyToPay before starting your SamsungPay flow. IsReadyToPay will call you back with the
 * status of Samsung Pay.
 *
 * TODO(Modify this s.t. the response listener also passes the reason for readiness, so that the merchant can take available actions)
 */
fun isReadyToPay(fragment: BraintreeFragment, listener: BraintreeResponseListener<Boolean>) {
    if (!isSamsungPayAvailable()) {
        listener.onResponse(false)
        fragment.postCallback(ConfigurationException("Samsung SDK not found on classpath. " +
                "Please add the Samsung Pay SDK as a dependency to your build.gradle file. " +
                "For more information refer to the docs http://developers.braintreepayments.com/todo/docs/link/here"))

        return
    }

    getPartnerInfo(fragment, BraintreeResponseListener { info ->
        val samsungPay = SamsungPay(fragment.applicationContext, info)

        samsungPay.getSamsungPayStatus(object : StatusListener {
            override fun onSuccess(status: Int, bundle: Bundle) {
                when (status) {
                    com.samsung.android.sdk.samsungpay.v2.SamsungPay.SPAY_NOT_SUPPORTED,
                    com.samsung.android.sdk.samsungpay.v2.SamsungPay.SPAY_NOT_READY -> listener.onResponse(false)
                    com.samsung.android.sdk.samsungpay.v2.SamsungPay.SPAY_READY -> listener.onResponse(true)
                }
            }

            override fun onFail(errorCode: Int, bundle: Bundle) {
                fragment.postCallback(ConfigurationException("SamsungPay is not available"))
            }
        })
    })
}

/**
 * startSamsungPay takes a PaymentInfo.Builder and starts intitiates the SamsungPay flow
 * with the normal UI provied by SamsungPay.
 */
fun startSamsungPay(fragment: BraintreeFragment, paymentInfoBuilder: PaymentInfo.Builder, listener: SamsungPayTransactionListener) {
    getPartnerInfo(fragment, BraintreeResponseListener { info ->
        // TODO remove and pull these values from SamsungPayConfiguration
        var brandsFromConfiguration: ArrayList<String> = ArrayList()
        brandsFromConfiguration.add("Visa")
        brandsFromConfiguration.add("Mastercard")

        paymentInfoBuilder.setMerchantName(merchantName)
                .setMerchantId(merchantId)
                .setOrderNumber("1234567890")
                .setAllowedCardBrands(samsungPayAcceptedCardBrands(brandsFromConfiguration))

        val paymentManager = PaymentManager(fragment.applicationContext, info)

        val paymentInfo = paymentInfoBuilder.build()

        val callbacks = SamsungPayTransactionInfoListenerFacade(fragment, paymentInfo, paymentManager, listener)
        paymentManager.startInAppPay(paymentInfoBuilder.build(), callbacks)
    })
}

/**
 * startSamsungPay takes a CustomSheetInfo.Builder and starts intitiates the SamsungPay flow
 * with some custom UI provided by you.
 */
fun startSamsungPay(fragment: BraintreeFragment, customSheetPaymentInfoBuilder: CustomSheetPaymentInfo.Builder, listener: SamsungCustomSheetTransactionListener) {
    getPartnerInfo(fragment, BraintreeResponseListener { info ->
        var brandsFromConfiguration: ArrayList<String> = ArrayList()
        brandsFromConfiguration.add("Visa")
        brandsFromConfiguration.add("Mastercard")

        customSheetPaymentInfoBuilder.setMerchantId(merchantId)
                .setMerchantName(merchantName)
                .setAllowedCardBrands(samsungPayAcceptedCardBrands(brandsFromConfiguration));

        val paymentManager = PaymentManager(fragment.applicationContext, info)

        paymentManager.startInAppPayWithCustomSheet(customSheetPaymentInfoBuilder.build(), SamsungPayCustomSheetTransactionInfoListenerFacade(fragment, paymentManager, listener))
    })
}

private fun samsungPayAcceptedCardBrands(braintreeAcceptedCardBrands: ArrayList<String>): List<SpaySdk.Brand> {
    var samsungAcceptedList = ArrayList<SpaySdk.Brand>()

    for (braintreeAcceptedCardBrand in braintreeAcceptedCardBrands) {
        when (braintreeAcceptedCardBrand.toLowerCase()) {
            "visa" -> samsungAcceptedList.add(SpaySdk.Brand.VISA)
            "mastercard" -> samsungAcceptedList.add(SpaySdk.Brand.MASTERCARD)
            "discover" -> samsungAcceptedList.add(SpaySdk.Brand.DISCOVER)
            "american express" -> samsungAcceptedList.add(SpaySdk.Brand.AMERICANEXPRESS)
        }
    }

    return samsungAcceptedList
}

private fun getPartnerInfo(fragment: BraintreeFragment, listener: BraintreeResponseListener<PartnerInfo>) {
    fragment.waitForConfiguration { configuration ->
        val bundle = Bundle()
        bundle.putString(SamsungPay.PARTNER_SERVICE_TYPE, SpaySdk.ServiceType.INAPP_PAYMENT.toString())

        val info = PartnerInfo(serviceId, bundle)

        listener.onResponse(info)
    }
}

private fun isSamsungPayAvailable(): Boolean {
    return ClassHelper.isClassAvailable("com.samsung.android.sdk.samsungpay.v2.SamsungPay")
}
