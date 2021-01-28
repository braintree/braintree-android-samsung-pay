package com.braintreepayments.api

import android.os.Bundle
import com.samsung.android.sdk.samsungpay.v2.SpaySdk
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet

internal class SamsungPayCustomTransactionListenerWrapper(
        val paymentManager: PaymentManager,
        val merchantCallback: SamsungPayCustomTransactionUpdateListener,
        val braintreeClient: BraintreeClient,
        val callback: SamsungPayTransactionCallback
) : PaymentManager.CustomSheetTransactionInfoListener {

    override fun onSuccess(response: CustomSheetPaymentInfo?, paymentCredential: String?, extraPaymentData: Bundle?) {
        if (paymentCredential != null) {
            callback.onResult(SamsungPayNonce.fromPaymentData(paymentCredential), null)
            braintreeClient.sendAnalyticsEvent("samsung-pay.request-payment.success")
        }

        if (response != null) {
            val bundle = extraPaymentData ?: Bundle()
            merchantCallback.onSuccess(response, bundle)
        }

    }

    override fun onFailure(errorCode: Int, extras: Bundle?) {
        if (errorCode == SpaySdk.ERROR_USER_CANCELED) {
            // TODO: fix
//            fragment.postCancelCallback(BraintreeRequestCodes.SAMSUNG_PAY)
            braintreeClient.sendAnalyticsEvent("samsung-pay.request-payment.user-canceled")
        } else {
            callback.onResult(null, SamsungPayException(errorCode, extras))
            braintreeClient.sendAnalyticsEvent("samsung-pay.request-payment.failed")
        }
    }

    override fun onCardInfoUpdated(cardInfo: CardInfo?, customSheet: CustomSheet?) {
        if (cardInfo != null && customSheet != null) {
            merchantCallback.onCardInfoUpdated(cardInfo, customSheet)
            paymentManager.updateSheet(customSheet)
        }
    }
}
