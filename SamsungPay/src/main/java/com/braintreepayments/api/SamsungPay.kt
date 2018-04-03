package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.exceptions.ConfigurationException
import com.braintreepayments.api.interfaces.BraintreeResponseListener
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.SpaySdk
import com.samsung.android.sdk.samsungpay.v2.StatusListener

object SamsungPay {

    internal val isSamsungPaySDKAvailable: Boolean
        get() {
            try {
                Class.forName("com.samsung.android.sdk.samsungpay.v2.SamsungPay")
                return true
            } catch (e: ClassNotFoundException) {
                return false
            }

        }

    fun isReadyToPay(fragment: BraintreeFragment, listener: BraintreeResponseListener<Boolean>) {
        if (!isSamsungPaySDKAvailable) {
            listener.onResponse(false)
            fragment.postCallback(ConfigurationException("Samsung SDK not found on classpath." +
                    "Please add the Samsung Pay SDK as a depdency to your build.gradle file." +
                    "For more information refer to the docs http://developers.braintreepayments.com/todo/docs/link/here"))
        }

        fragment.waitForConfiguration { configuration ->
            val hardCodedServiceId = "1f7bb987065d4e16aa5f1f"
            // TODO pull SamsungPay stanza out of out of configuration, look for serviceId

            val info = PartnerInfo(hardCodedServiceId)

            val samsungPay = com.samsung.android.sdk.samsungpay.v2.SamsungPay(fragment.applicationContext, info)

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
        }
    }

    // TODO: find appropriate testing values and flesh this out when configuration is complete
//    fun createPaymentBuilder(fragment: BraintreeFragment, listener: BraintreeResponseListener<PaymentInfo.Builder>) {
//        fragment.waitForConfiguration { configuration ->
//            var builder = PaymentInfo.Builder()
//                    .setMerchantName(configuration.samsungPayConfiguration.merchantName)
//                    .setMerchantId(configuration.samsungPayConfiguration.merchantId)
//                    .setAllowedCardBrands(samsungPayAcceptedCardBrands(configuration.samsungPayConfiguration.acceptedCardBrands))
//
//            listener.onResponse(builder)
//        }
//    }

    private fun samsungPayAcceptedCardBrands(braintreeAcceptedCardBrands: ArrayList<String>) : List<SpaySdk.Brand> {
        var samsungAcceptedList = ArrayList<SpaySdk.Brand>()

        for (braintreeAcceptedCardBrand in braintreeAcceptedCardBrands) {
            when (braintreeAcceptedCardBrand) {
                "visa" -> samsungAcceptedList.add(SpaySdk.Brand.VISA)
                "mastercard" -> samsungAcceptedList.add(SpaySdk.Brand.MASTERCARD)
                "discover" -> samsungAcceptedList.add(SpaySdk.Brand.DISCOVER)
                "american express" -> samsungAcceptedList.add(SpaySdk.Brand.AMERICANEXPRESS)
            }
        }

        return samsungAcceptedList
    }
}
