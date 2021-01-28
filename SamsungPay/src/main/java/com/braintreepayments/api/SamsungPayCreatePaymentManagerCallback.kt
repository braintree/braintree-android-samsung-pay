package com.braintreepayments.api

import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager

interface SamsungPayCreatePaymentManagerCallback {
    fun onResult(paymentManager: PaymentManager?, error: Exception?)
}