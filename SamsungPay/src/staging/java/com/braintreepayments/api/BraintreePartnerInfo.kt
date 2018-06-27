package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.models.SamsungPayConfiguration
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo

internal class BraintreePartnerInfo(
    val configuration: SamsungPayConfiguration,
    data: Bundle
) : PartnerInfo(configuration.serviceId.let {
    "bb8dcda46bd1400db8fecd"
}, data)
