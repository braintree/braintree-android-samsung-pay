package com.braintreepayments.api.interfaces

import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo

/**
 * SamsungPayTransactionListener is an interface that defines how your app should react to changes
 * in the Customer's information. For example, if the card or address changes, you have the opportunity
 * to update the amount. Both methods are nullable, if the amount should not change, simply return
 * null.
 */
interface SamsungPayTransactionUpdateListener {
    fun onAddressUpdated(paymentInfo: PaymentInfo): PaymentInfo.Amount?
    fun onCardInfoUpdated(cardInfo: CardInfo): PaymentInfo.Amount?
}


