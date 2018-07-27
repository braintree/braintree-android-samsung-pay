package com.braintreepayments.demo.samsungpay

import android.app.AlertDialog
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import com.braintreepayments.api.BraintreeFragment
import com.braintreepayments.api.SamsungPay
import com.braintreepayments.api.exceptions.InvalidArgumentException
import com.braintreepayments.api.exceptions.SamsungPayException
import com.braintreepayments.api.interfaces.*
import com.braintreepayments.api.models.BinData
import com.braintreepayments.api.models.BraintreeRequestCodes
import com.braintreepayments.api.models.PaymentMethodNonce
import com.braintreepayments.api.models.SamsungPayNonce
import com.braintreepayments.demo.samsungpay.internal.ApiClient
import com.braintreepayments.demo.samsungpay.models.Transaction
import com.samsung.android.sdk.samsungpay.v2.SpaySdk.*
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.*
import retrofit.Callback
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Response
import java.util.*

class MainKotlinActivity : AppCompatActivity(), BraintreeErrorListener, BraintreeCancelListener,
    PaymentMethodNonceCreatedListener {

    private var mUseProduction: CheckBox? = null
    private var mTokenizeButton: Button? = null
    private var mTransactButton: Button? = null
    private var mBraintreeFragment: BraintreeFragment? = null
    private var mBillingAddressDetails: TextView? = null
    private var mShippingAddressDetails: TextView? = null
    private var mNonceDetails: TextView? = null
    private var mPaymentManager: PaymentManager? = null
    private var mPaymentMethodNonce: PaymentMethodNonce? = null
    private var mAuthorization: String? = null
    private var mEndpoint: String? = null

    private val customSheet: CustomSheet
        get() {
            val sheet = CustomSheet()

            val billingAddressControl = AddressControl("billingAddressId", SheetItemType.BILLING_ADDRESS)
            billingAddressControl.addressTitle = "Billing Address"
            billingAddressControl.sheetUpdatedListener = SheetUpdatedListener { controlId, customSheet ->
                Log.d("billing sheet updated", controlId)

                mPaymentManager!!.updateSheet(customSheet)
            }
            sheet.addControl(billingAddressControl)

            val shippingAddressControl = AddressControl("shippingAddressId", SheetItemType.SHIPPING_ADDRESS)
            shippingAddressControl.addressTitle = "Shipping Address"
            shippingAddressControl.sheetUpdatedListener = SheetUpdatedListener { controlId, customSheet ->
                Log.d("shipping sheet updated", controlId)

                mPaymentManager!!.updateSheet(customSheet)
            }
            sheet.addControl(shippingAddressControl)

            val amountBoxControl = AmountBoxControl("amountID", "USD")
            amountBoxControl.addItem("itemId", "Items", 1.0, "")
            amountBoxControl.addItem("taxId", "Tax", 1.0, "")
            amountBoxControl.addItem("shippingId", "Shipping", 10.0, "")
            amountBoxControl.addItem("interestId", "Interest [ex]", 0.0, "")
            amountBoxControl.setAmountTotal(1.0, AmountConstants.FORMAT_TOTAL_PRICE_ONLY)
            amountBoxControl.addItem(3, "fuelId", "FUEL", 0.0, "Pending")
            sheet.addControl(amountBoxControl)

            return sheet
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mUseProduction = findViewById(R.id.use_production)
        mTokenizeButton = findViewById(R.id.samsung_pay_tokenize)
        mTransactButton = findViewById(R.id.samsung_pay_transact)
        mBillingAddressDetails = findViewById(R.id.billing_address_details)
        mShippingAddressDetails = findViewById(R.id.shipping_address_details)
        mNonceDetails = findViewById(R.id.nonce_details)
        mNonceDetails!!.visibility = View.VISIBLE

        startBraintree()
    }

    private fun startBraintree() {
        mTokenizeButton!!.isEnabled = false
        mTransactButton!!.isEnabled = false

        if (mBraintreeFragment != null) {
            fragmentManager.beginTransaction()
                .remove(mBraintreeFragment)
                .commit()
        }

        if (mUseProduction!!.isChecked) {
            mAuthorization = PRODUCTION_TOKENIZATION_KEY
            mEndpoint = PRODUCTION_ENDPOINT
        } else {
            mAuthorization = SANDBOX_TOKENIZATION_KEY
            mEndpoint = SANDBOX_ENDPOINT
        }

        try {
            mBraintreeFragment = BraintreeFragment.newInstance(this, mAuthorization)
        } catch (ignored: InvalidArgumentException) {
        }

        SamsungPay.isReadyToPay(mBraintreeFragment!!, BraintreeResponseListener { availability ->
            when (availability.status) {
                SPAY_READY -> mTokenizeButton!!.isEnabled = true
                SPAY_NOT_READY -> {
                    val reason = availability.reason
                    if (reason == ERROR_SPAY_APP_NEED_TO_UPDATE) {
                        showDialog("Need to update Samsung Pay app...")
                        SamsungPay.goToUpdatePage(mBraintreeFragment!!)
                    } else if (reason == ERROR_SPAY_SETUP_NOT_COMPLETED) {
                        showDialog("Samsung Pay setup not completed...")
                        SamsungPay.activateSamsungPay(mBraintreeFragment!!)
                    } else if (reason == SamsungPay.SPAY_NO_SUPPORTED_CARDS_IN_WALLET) {
                        showDialog("No supported cards in wallet")
                    }
                }
                SPAY_NOT_SUPPORTED -> {
                    showDialog("Samsung Pay is not supported")
                    mTokenizeButton!!.isEnabled = false
                }
            }
        })
    }

    fun tokenize(v: View) {
        SamsungPay.createPaymentManager(mBraintreeFragment!!, BraintreeResponseListener { paymentManager ->
            mPaymentManager = paymentManager

            SamsungPay.createPaymentInfo(mBraintreeFragment!!, BraintreeResponseListener { builder ->
                val paymentInfo = builder
                    .setAddressInPaymentSheet(CustomSheetPaymentInfo.AddressInPaymentSheet.NEED_BILLING_AND_SHIPPING)
                    .setCustomSheet(customSheet)
                    .setOrderNumber("order-number")
                    .build()


                SamsungPay.requestPayment(
                    mBraintreeFragment!!,
                    mPaymentManager!!,
                    paymentInfo,
                    object : SamsungPayCustomTransactionUpdateListener {
                        override fun onSuccess(response: CustomSheetPaymentInfo, extraPaymentData: Bundle) {
                            val customSheet = response.customSheet
                            val billingAddressControl =
                                customSheet.getSheetControl("billingAddressId") as AddressControl
                            val billingAddress = billingAddressControl.address

                            val shippingAddress = response.paymentShippingAddress

                            displayAddresses(billingAddress, shippingAddress)
                        }

                        override fun onCardInfoUpdated(cardInfo: CardInfo, customSheet: CustomSheet) {
                            val amountBoxControl = customSheet.getSheetControl("amountID") as AmountBoxControl
                            amountBoxControl.updateValue("itemId", 1.0)
                            amountBoxControl.updateValue("taxId", 1.0)
                            amountBoxControl.updateValue("shippingId", 1.0)
                            amountBoxControl.updateValue("interestId", 1.0)
                            amountBoxControl.updateValue("fuelId", 1.0)

                            customSheet.updateControl(amountBoxControl)
                        }
                    })
            })
        })
    }

    override fun onError(error: Exception) {
        if (error is SamsungPayException) {
            val (code) = error

            when (code) {

            }// handle accordingly
            // ...

            showDialog("Samsung Pay failed with error code " + error.code)
        }
    }

    override fun onPaymentMethodNonceCreated(paymentMethodNonce: PaymentMethodNonce) {
        mPaymentMethodNonce = paymentMethodNonce
        mTransactButton!!.isEnabled = true

        displayPaymentMethodNonce(paymentMethodNonce)
    }

    override fun onCancel(requestCode: Int) {
        if (requestCode == BraintreeRequestCodes.SAMSUNG_PAY) {
            Log.d("SamsungPay", "User canceled payment.")
        }
    }

    private fun displayAddresses(
        billingAddress: CustomSheetPaymentInfo.Address?,
        shippingAddress: CustomSheetPaymentInfo.Address?
    ) {
        if (billingAddress != null) {
            mBillingAddressDetails!!.text = TextUtils.join(
                "\n", Arrays.asList(
                    "Billing Address",
                    "Addressee: " + shippingAddress!!.addressee,
                    "AddressLine1: " + shippingAddress.addressLine1,
                    "AddressLine2: " + shippingAddress.addressLine2,
                    "City: " + shippingAddress.city,
                    "PostalCode: " + shippingAddress.postalCode,
                    "CountryCode: " + shippingAddress.countryCode
                )
            )
        }

        if (shippingAddress != null) {
            mShippingAddressDetails!!.text = TextUtils.join(
                "\n", Arrays.asList(
                    "Shipping Address",
                    "Addressee: " + shippingAddress.addressee,
                    "AddressLine1: " + shippingAddress.addressLine1,
                    "AddressLine2: " + shippingAddress.addressLine2,
                    "City: " + shippingAddress.city,
                    "PostalCode: " + shippingAddress.postalCode,
                    "CountryCode: " + shippingAddress.countryCode
                )
            )
        }
    }

    private fun displayPaymentMethodNonce(paymentMethodNonce: PaymentMethodNonce) {
        val nonce = paymentMethodNonce as SamsungPayNonce
        val display = nonce.cardType + " " + nonce.description + "\n" +
                "Token: " + nonce.nonce + "\n" +
                binDataString(nonce.binData!!)

        mNonceDetails!!.text = display
    }

    private fun binDataString(binData: BinData): String {
        return "Bin Data: \n" +
                "         - Prepaid: " + binData.healthcare + "\n" +
                "         - Healthcare: " + binData.healthcare + "\n" +
                "         - Debit: " + binData.debit + "\n" +
                "         - Durbin Regulated: " + binData.durbinRegulated + "\n" +
                "         - Commercial: " + binData.commercial + "\n" +
                "         - Payroll: " + binData.payroll + "\n" +
                "         - Issuing Bank: " + binData.issuingBank + "\n" +
                "         - Country of Issuance: " + binData.countryOfIssuance + "\n" +
                "         - Product Id: " + binData.productId
    }

    fun transact(v: View) {
        showSpinner(true)

        val callback = object : Callback<Transaction> {
            override fun success(transaction: Transaction, response: Response) {
                showSpinner(false)

                if (transaction.message != null && transaction.message.startsWith("created")) {
                    showDialog("Successful transaction: " + transaction.message)
                } else {
                    if (TextUtils.isEmpty(transaction.message)) {
                        showDialog("Server response was empty or malformed")
                    } else {
                        showDialog("Error creating transaction: " + transaction.message)
                    }
                }
            }

            override fun failure(error: RetrofitError) {
                showSpinner(false)

                showDialog(
                    "Unable to create a transaction. Response Code: " +
                            error.response.status + " Response body: " +
                            error.response.body
                )
            }
        }

        getApiClient(mEndpoint)?.createTransaction(
            mPaymentMethodNonce!!.nonce, "SamsungPayFD", callback)
    }

    protected fun showDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, which -> dialog.dismiss() }
            .show()
    }

    protected fun showSpinner(show: Boolean) {
        val pb = findViewById<ProgressBar>(R.id.progressBar)
        pb.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.INVISIBLE
    }

    companion object {
        private val PRODUCTION_TOKENIZATION_KEY = "production_t2wns2y2_dfy45jdj3dxkmz5m"
        private val SANDBOX_TOKENIZATION_KEY = "sandbox_tmxhyf7d_dcpspy2brwdjr3qn"

        private val PRODUCTION_ENDPOINT = "https://executive-sample-merchant.herokuapp.com"
        private val SANDBOX_ENDPOINT = "https://braintree-sample-merchant.herokuapp.com"
        private var sApiClient: ApiClient? = null

        internal fun getApiClient(endpoint: String?): ApiClient? {
            class ApiClientRequestInterceptor : RequestInterceptor {
                override fun intercept(request: RequestInterceptor.RequestFacade) {
                    request.addHeader("User-Agent", "braintree/android-demo-app/" + BuildConfig.VERSION_NAME)
                    request.addHeader("Accept", "application/json")
                }
            }

            if (sApiClient == null) {
                sApiClient = RestAdapter.Builder()
                    .setEndpoint(endpoint!!)
                    .setRequestInterceptor(ApiClientRequestInterceptor())
                    .build()
                    .create<ApiClient>(ApiClient::class.java!!)
            }

            return sApiClient
        }
    }
}
