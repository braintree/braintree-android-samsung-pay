package com.braintreepayments.api

import android.os.Bundle

data class SamsungPayException(val code: Int, val extras: Bundle?) : java.lang.Exception()