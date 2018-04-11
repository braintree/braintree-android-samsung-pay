package com.braintreepayments.api.exceptions

import android.os.Bundle

data class SamsungPayException(val code: Int, val extras: Bundle) : java.lang.Exception()