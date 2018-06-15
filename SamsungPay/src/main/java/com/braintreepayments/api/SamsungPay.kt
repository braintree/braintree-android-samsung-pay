@file:JvmName("SamsungPay")

package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.exceptions.SamsungPayException
import com.braintreepayments.api.interfaces.BraintreeErrorListener
import com.braintreepayments.api.interfaces.BraintreeResponseListener
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener
import com.braintreepayments.api.internal.ClassHelper
import com.braintreepayments.api.models.MetadataBuilder
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.SamsungPay
import com.samsung.android.sdk.samsungpay.v2.SpaySdk
import com.samsung.android.sdk.samsungpay.v2.SpaySdk.*
import com.samsung.android.sdk.samsungpay.v2.StatusListener
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import org.json.JSONObject
import java.util.*

enum class SamsungPayStatus(val status: Int) {
    READY(SPAY_READY),
    NOT_READY(SPAY_NOT_READY),
    NOT_SUPPORTED(SPAY_NOT_SUPPORTED);

    companion object {
        fun valueOf(status: Int): SamsungPayStatus? = SamsungPayStatus.values().find { it.status == status }
    }
}

enum class SamsungPayErrorReason(val reason: Int) {
    SETUP_NOT_COMPLETED(ERROR_SPAY_SETUP_NOT_COMPLETED),
    NEED_TO_UPDATE_SPAY_APP(ERROR_SPAY_APP_NEED_TO_UPDATE),
    UNKNOWN(0);

    companion object {
        fun valueOf(reason: Int): SamsungPayErrorReason? = SamsungPayErrorReason.values().find { it.reason == reason }
    }
}

class SamsungPayAvailability(private val status: Int, private val bundle: Bundle) {
    fun status(): SamsungPayStatus {
        return SamsungPayStatus.valueOf(status) ?: SamsungPayStatus.NOT_SUPPORTED
    }

    fun errorReason(): SamsungPayErrorReason {
        val errorIndex = bundle.getInt(SamsungPay.EXTRA_ERROR_REASON)
        return SamsungPayErrorReason.valueOf(errorIndex) ?: SamsungPayErrorReason.UNKNOWN
    }
}

/**
 * Call isReadyToPay before starting your Samsung Pay flow. isReadyToPay will call you back with the
 * status of Samsung Pay. If the Samsung Pay jar has not been included, or if the status of
 * Samsung Pay is anything but [SamsungPayStatus.SPAY_READY], the listener will be called back
 * with a value of false. If the Samsung Pay callback fails and returns an error, that error
 * will be posted to the [BraintreeErrorListener] callback attached to the instance of
 * [BraintreeFragment] passed in here.
 *
 * TODO(Modify this s.t. the response listener also passes the reason for readiness, so that the merchant can take available actions)
 *
 * @param [fragment] TODO
 * @param [listener] TODO
 */
fun isReadyToPay(fragment: BraintreeFragment, listener: BraintreeResponseListener<SamsungPayAvailability>) {
    if (!isSamsungPayAvailable()) {
        listener.onResponse(SamsungPayAvailability(SPAY_NOT_SUPPORTED, Bundle()))
        return
    }

    getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
        val samsungPay = getSamsungPay(fragment, braintreePartnerInfo)

        samsungPay.getSamsungPayStatus(object : StatusListener {
            override fun onSuccess(status: Int, bundle: Bundle) {
                listener.onResponse(SamsungPayAvailability(status, bundle))
            }

            override fun onFail(errorCode: Int, bundle: Bundle) {
                // TODO: is it even necessary to post a response, given that we post the exception?
                listener.onResponse(SamsungPayAvailability(SPAY_NOT_SUPPORTED, bundle))
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
        val bundle = Bundle()

        bundle.putString(SpaySdk.PARTNER_SERVICE_TYPE, SpaySdk.ServiceType.INAPP_PAYMENT.toString())
        bundle.putBoolean(PaymentManager.EXTRA_KEY_TEST_MODE, true)

        val clientSdkMetadataJson = JSONObject()
        clientSdkMetadataJson.put("clientSdkMetadata", MetadataBuilder()
                .integration(fragment.integrationType)
                .sessionId(fragment.sessionId)
                .version()
                .build())
        bundle.putString("additionalData", clientSdkMetadataJson.toString())

        listener.onResponse(BraintreePartnerInfo(configuration.samsungPay, bundle))
    }
}

internal fun getSamsungPay(fragment: BraintreeFragment, info: PartnerInfo): SamsungPay {
    return SamsungPay(fragment.applicationContext, info)
}

internal fun getPaymentManager(fragment: BraintreeFragment, info: PartnerInfo): PaymentManager {
    return PaymentManager(fragment.applicationContext, info)
}
