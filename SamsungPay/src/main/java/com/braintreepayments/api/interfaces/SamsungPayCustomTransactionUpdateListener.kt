package com.braintreepayments.api.interfaces

import android.os.Bundle
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet

interface SamsungPayCustomTransactionUpdateListener {
    fun onCardInfoUpdated(cardInfo: CardInfo, customSheet: CustomSheet)
    fun onSuccess(response: CustomSheetPaymentInfo, extraPaymentData: Bundle)
}
