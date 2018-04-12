package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.interfaces.SamsungPayTransactionListener
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager

internal class SamsungPayTransactionInfoListenerFacade(val fragment: BraintreeFragment,
                                                       val paymentInfo: PaymentInfo,
                                                       val paymentManager: PaymentManager,
                                                       val merchantCallback: SamsungPayTransactionListener) : PaymentManager.TransactionInfoListener {

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

