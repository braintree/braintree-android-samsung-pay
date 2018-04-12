package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.interfaces.SamsungPayCustomSheetTransactionListener
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet

internal class SamsungPayCustomSheetTransactionListenerFacade(val fragment: BraintreeFragment,
                                                              val paymentManager: PaymentManager,
                                                              val merchantCallback: SamsungPayCustomSheetTransactionListener) : PaymentManager.CustomSheetTransactionInfoListener {

    override fun onSuccess(p0: CustomSheetPaymentInfo?, p1: String?, p2: Bundle?) {
        TODO("Tokenize") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onFailure(p0: Int, p1: Bundle?) {
        TODO("post an exception on BraintreeFragment back to the user") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCardInfoUpdated(cardInfo: CardInfo?, customSheet: CustomSheet?) {
        if (cardInfo != null && customSheet != null) {
            merchantCallback.onCardInfoUpdated(cardInfo, customSheet)
            paymentManager.updateSheet(customSheet)
        }
    }
}
