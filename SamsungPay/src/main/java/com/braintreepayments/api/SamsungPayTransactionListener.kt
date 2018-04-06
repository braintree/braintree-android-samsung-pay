package com.braintreepayments.api

import android.os.Bundle
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet

/**
 * SamsungPayTransactionListener is an interface that defines how your app should react to changes
 * in the Customer's information. For example, if the card or address changes, you have the opportunity
 * to update the amount. Both methods are nullable, if the amount should not change, simply return
 * null.
 */
interface SamsungPayTransactionListener {
    fun onAddressUpdated(paymentInfo: PaymentInfo): PaymentInfo.Amount?
    fun onCardInfoUpdated(cardInfo: CardInfo): PaymentInfo.Amount?
}

interface SamsungCustomSheetTransactionListener {
    fun onCardInfoUpdated(cardInfo: CardInfo, customSheet: CustomSheet)
}

internal class SamsungPayCustomSheetTransactionInfoListenerFacade(val fragment: BraintreeFragment,
                                                                  val paymentManager: PaymentManager,
                                                                  val merchantCallback: SamsungCustomSheetTransactionListener): PaymentManager.CustomSheetTransactionInfoListener {

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


internal class SamsungPayTransactionInfoListenerFacade(val fragment: BraintreeFragment,
                                                       val paymentInfo: PaymentInfo,
                                                       val paymentManager: PaymentManager,
                                                       val merchantCallback: SamsungPayTransactionListener): PaymentManager.TransactionInfoListener {

    override fun onSuccess(response: PaymentInfo?, paymentCredential: String?, extraPaymentData: Bundle?) {
        // Tokenize with braintree, callback with braintreefragment
    }

    override fun onFailure(errorCode: Int, errorData: Bundle?) {
        // Callback with Braintree fragment
    }

    override fun onAddressUpdated(paymentInfo: PaymentInfo?) {
        paymentInfo?.let { _paymentInfo ->
            var updatedAmount = merchantCallback.onAddressUpdated(_paymentInfo)

            if (updatedAmount == null) {
                updatedAmount = _paymentInfo.amount
            }

            paymentManager.updateAmount(updatedAmount)
        }
    }

    override fun onCardInfoUpdated(cardInfo: CardInfo?) {
        cardInfo?.let { _cardInfo ->
            var updatedAmount = merchantCallback.onCardInfoUpdated(_cardInfo)

            if (updatedAmount == null) {
                updatedAmount = paymentInfo.amount
            }

            paymentManager.updateAmount(updatedAmount)
        }
    }
}