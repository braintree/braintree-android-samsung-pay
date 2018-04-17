package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.models.SamsungPayConfiguration;
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.SpaySdk

internal class BraintreePartnerInfo(data: Bundle,
                                    val configuration: SamsungPayConfiguration): PartnerInfo(configuration.serviceId, data)
