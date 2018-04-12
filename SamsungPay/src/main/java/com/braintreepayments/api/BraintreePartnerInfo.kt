package com.braintreepayments.api

import android.os.Bundle
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.SpaySdk

internal class BraintreePartnerInfo(serviceId: String, data: Bundle, val acceptedCardBrands: List<SpaySdk.Brand>): PartnerInfo(serviceId, data)
