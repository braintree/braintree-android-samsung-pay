package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.exceptions.SamsungPayException
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener
import com.braintreepayments.api.models.BraintreeRequestCodes
import com.braintreepayments.api.models.SamsungPayNonce
import com.samsung.android.sdk.samsungpay.v2.SpaySdk
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet

internal class SamsungPayCustomTransactionListenerWrapper(
    val fragment: BraintreeFragment,
    val paymentManager: PaymentManager,
    val merchantCallback: SamsungPayCustomTransactionUpdateListener
) : PaymentManager.CustomSheetTransactionInfoListener {

    override fun onSuccess(response: CustomSheetPaymentInfo?, paymentCredential: String?, extraPaymentData: Bundle?) {
        if (paymentCredential != null) {
            fragment.postCallback(SamsungPayNonce.fromPaymentData(paymentCredential))
            fragment.sendAnalyticsEvent("samsung-pay.request-payment.success")
        }

        if (response != null) {
            val bundle = extraPaymentData ?: Bundle()
            merchantCallback.onSuccess(response, bundle)
        }

    }

    override fun onFailure(errorCode: Int, extras: Bundle?) {
        if (errorCode == SpaySdk.ERROR_USER_CANCELED) {
            fragment.postCancelCallback(BraintreeRequestCodes.SAMSUNG_PAY)
            fragment.sendAnalyticsEvent("samsung-pay.request-payment.user-canceled")
        } else {
            fragment.postCallback(SamsungPayException(errorCode, extras))
            fragment.sendAnalyticsEvent("samsung-pay.request-payment.failed")
        }
    }

    override fun onCardInfoUpdated(cardInfo: CardInfo?, customSheet: CustomSheet?) {
        if (cardInfo != null && customSheet != null) {
            merchantCallback.onCardInfoUpdated(cardInfo, customSheet)
            paymentManager.updateSheet(customSheet)
        }
    }
}
