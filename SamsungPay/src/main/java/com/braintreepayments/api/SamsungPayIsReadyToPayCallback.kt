package com.braintreepayments.api

interface SamsungPayIsReadyToPayCallback {
    fun onResult(samsungPayAvailability: SamsungPayAvailability?, error: Exception?)
}