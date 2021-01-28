package com.braintreepayments.api

interface SamsungPayRequestCardInfoCallback {
    fun onResult(cardInfoAvailability: SamsungPayAvailability?, error: Exception?)
}