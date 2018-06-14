package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.exceptions.SamsungPayException
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener
import com.braintreepayments.api.models.SamsungPayNonce
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
        }
    }

    override fun onFailure(errorCode: Int, extras: Bundle?) {
        fragment.postCallback(SamsungPayException(errorCode, extras))
    }

    override fun onCardInfoUpdated(cardInfo: CardInfo?, customSheet: CustomSheet?) {
        if (cardInfo != null && customSheet != null) {
            merchantCallback.onCardInfoUpdated(cardInfo, customSheet)
            paymentManager.updateSheet(customSheet)
        }
    }
}
