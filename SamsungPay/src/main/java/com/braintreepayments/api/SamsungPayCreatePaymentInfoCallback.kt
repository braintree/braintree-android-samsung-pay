package com.braintreepayments.api

import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo

interface SamsungPayCreatePaymentInfoCallback {
    fun onResult(builder: CustomSheetPaymentInfo.Builder, error: Exception?)
}