package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.exceptions.SamsungPayException
import com.braintreepayments.api.interfaces.SamsungPayTransactionUpdateListener
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager

internal class SamsungPayTransactionListenerWrapper(
    val fragment: BraintreeFragment,
    val paymentInfo: PaymentInfo,
    val paymentManager: PaymentManager,
    val merchantCallback: SamsungPayTransactionUpdateListener
) : PaymentManager.TransactionInfoListener {

    override fun onSuccess(response: PaymentInfo?, paymentCredential: String?, extraPaymentData: Bundle?) {
        TODO("Tokenize with braintree, callback with braintreefragment")
    }

    override fun onFailure(errorCode: Int, errorData: Bundle?) {
        fragment.postCallback(SamsungPayException(errorCode, errorData))
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

