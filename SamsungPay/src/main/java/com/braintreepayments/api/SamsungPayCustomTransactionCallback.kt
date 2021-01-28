package com.braintreepayments.api

import com.braintreepayments.api.models.SamsungPayNonce

interface SamsungPayCustomTransactionCallback {
    fun onResult(samsungPayNonce: SamsungPayNonce?, error: Exception?)
}