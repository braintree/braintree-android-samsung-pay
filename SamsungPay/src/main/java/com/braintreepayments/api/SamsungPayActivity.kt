package com.braintreepayments.api

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet


class SamsungPayActivity : FragmentActivity(), PaymentManager.CustomSheetTransactionInfoListener {


    companion object {
        val EXTRA_RESULT_PAYMENT = "com.braintreepayments.api.EXTRA_RESULT_PAYMENT"
        val EXTRA_RESULT_FAILURE_CODE = "com.braintreepayments.api.EXTRA_FAILURE_CODE"
        val EXTRA_RECREATING = "com.braintreepayments.api.EXTRA_RECREATING"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(savedInstanceState != null && savedInstanceState.getBoolean(EXTRA_RECREATING)) {
            return
        }

        val customSheetPaymentInfo = intent.getParcelableExtra<CustomSheetPaymentInfo>("CUSTOM_SHEET_PAYMENT_INFO")
        val info = intent.getParcelableExtra<PartnerInfo>("BRAINTREE_PARTNER_INFO")
        val paymentManager = PaymentManager(this, info)

        // TODO still to make better

        //Leave for Samsung Pay
        paymentManager.startInAppPayWithCustomSheet(
            customSheetPaymentInfo,
            this
        )
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        outState?.putBoolean(EXTRA_RECREATING, true)
    }

    override fun onSuccess(customerSheetPaymentInfo: CustomSheetPaymentInfo, paymentCredential: String?, bundle: Bundle?) {
        val intent = Intent()
            .putExtra(EXTRA_RESULT_PAYMENT, paymentCredential)

        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onFailure(failureCode: Int, bundle: Bundle?) {
        val intent = Intent()
            .putExtra(EXTRA_RESULT_FAILURE_CODE, failureCode)

        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onCardInfoUpdated(cardInfo: CardInfo?, customSheet: CustomSheet?) {
        // TODO not implemented
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        setResult(resultCode, data)
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}