package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.models.SamsungPayConfiguration
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo

internal class BraintreePartnerInfo(
    data: Bundle,
    val configuration: SamsungPayConfiguration
) : PartnerInfo(
    configuration.serviceId.let {
        // TODO("Temporarily set the Service ID")
        "bb8dcda46bd1400db8fecd"
    }, data)
