package com.braintreepayments.api

interface SamsungPayTransactionCallback {
    fun onResult(samsungPayNonce: SamsungPayNonce?, error: Exception?)
}