package com.braintreepayments.api

internal interface SamsungPayGetPartnerInfoCallback {
    fun onResult(partnerInfo: BraintreePartnerInfo?, error: Exception?)
}