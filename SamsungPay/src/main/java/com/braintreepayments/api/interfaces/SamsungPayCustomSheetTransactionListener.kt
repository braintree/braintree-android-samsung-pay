package com.braintreepayments.api.interfaces

import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet

interface SamsungPayCustomSheetTransactionListener {
    fun onCardInfoUpdated(cardInfo: CardInfo, customSheet: CustomSheet)
}
