package com.braintreepayments.samsungdxtest

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import com.braintreepayments.api.BraintreeFragment
import com.braintreepayments.api.interfaces.BraintreeErrorListener
import com.braintreepayments.api.interfaces.BraintreeResponseListener
import com.braintreepayments.api.isReadyToPay
import com.braintreepayments.api.startSamsungPay
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo
import java.lang.Exception

class MainActivity : AppCompatActivity(), View.OnClickListener, BraintreeErrorListener {

    private val TOKENIZATION_KEY = "sandbox_tmxhyf7d_dcpspy2brwdjr3qn"

    private var mSamsungPayButton: Button? = null
    private var mBraintreeFragment: BraintreeFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mSamsungPayButton = this.findViewById(R.id.samsung_pay_demo_launch_button)
        mBraintreeFragment = BraintreeFragment.newInstance(this, TOKENIZATION_KEY)
    }

    override fun onResume() {
        super.onResume()

        mSamsungPayButton?.setOnClickListener(this)

        mBraintreeFragment?.let { fragment ->
            isReadyToPay(fragment, BraintreeResponseListener { isReady ->
                mSamsungPayButton?.isEnabled = isReady
            })
        }
    }

    override fun onClick(v: View?) {
        mBraintreeFragment?.let { fragment ->
            startSamsungPay(fragment, createPaymentInfoBuilder())
        }
    }

    override fun onError(error: Exception?) {
        Log.e("Error", error?.message)
    }

    private fun createPaymentInfoBuilder(): PaymentInfo.Builder {
        return PaymentInfo.Builder()
                .setAmount(PaymentInfo.Amount.Builder()
                        .setCurrencyCode("USD")
                        .setItemTotalPrice("10")
                        .setShippingPrice("0")
                        .setTax("0")
                        .setTotalPrice("10").build())
    }
}
