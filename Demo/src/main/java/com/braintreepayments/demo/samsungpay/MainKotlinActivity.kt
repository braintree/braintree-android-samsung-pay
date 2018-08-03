package com.braintreepayments.demo.samsungpay

import android.app.AlertDialog
import android.os.Bundle
import android.support.annotation.IdRes
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
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

    companion object {
        private val EXTRA_AUTHORIZATION = "com.braintreepayments.demo.samsungpay.EXTRA_AUTHORIZATION"
        private val EXTRA_ENDPOINT = "com.braintreepayments.demo.samsungpay.EXTRA_ENDPOINT"

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
                    .create<ApiClient>(ApiClient::class.java)
            }

            return sApiClient
        }
    }

    private val environmentGroup: RadioGroup by bind(R.id.environment_group)
    private val tokenizeButton: Button by bind(R.id.samsung_pay_tokenize)
    private val transactButton: Button by bind(R.id.samsung_pay_transact)
    private val billingAddressDetails: TextView by bind(R.id.billing_address_details)
    private val shippingAddressDetails: TextView by bind(R.id.shipping_address_details)
    private val nonceDetails: TextView by bind(R.id.nonce_details)

    private var braintreeFragment: BraintreeFragment? = null
    private lateinit var paymentManager: PaymentManager
    private lateinit var paymentMethodNonce: PaymentMethodNonce
    private lateinit var authorization: String
    private lateinit var endpoint: String
    private var totalAmount = 1.0

    private val customSheet: CustomSheet
        get() {
            val sheet = CustomSheet()

            val billingAddressControl = AddressControl("billingAddressId", SheetItemType.BILLING_ADDRESS)
            billingAddressControl.addressTitle = "Billing Address"
            billingAddressControl.sheetUpdatedListener = SheetUpdatedListener { controlId, custosheet ->
                Log.d("billing sheet updated", controlId)

                paymentManager.updateSheet(custosheet)
            }
            sheet.addControl(billingAddressControl)

            val shippingAddressControl = AddressControl("shippingAddressId", SheetItemType.SHIPPING_ADDRESS)
            shippingAddressControl.addressTitle = "Shipping Address"
            shippingAddressControl.sheetUpdatedListener = SheetUpdatedListener { controlId, custosheet ->
                Log.d("shipping sheet updated", controlId)

                paymentManager.updateSheet(custosheet)
            }
            sheet.addControl(shippingAddressControl)

            val amountBoxControl = AmountBoxControl("amountID", "USD")
            amountBoxControl.addItem("itemId", "Items", 1.0, "")
            amountBoxControl.addItem("taxId", "Tax", 1.0, "")
            amountBoxControl.addItem("shippingId", "Shipping", 10.0, "")
            amountBoxControl.addItem("interestId", "Interest [ex]", 0.0, "")
            amountBoxControl.setAmountTotal(totalAmount, AmountConstants.FORMAT_TOTAL_PRICE_ONLY)
            amountBoxControl.addItem(3, "fuelId", "FUEL", 0.0, "Pending")
            sheet.addControl(amountBoxControl)

            return sheet
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val extras = intent.extras
        authorization = extras.getString(EXTRA_AUTHORIZATION, SANDBOX_TOKENIZATION_KEY)
        endpoint = extras.getString(EXTRA_ENDPOINT, SANDBOX_ENDPOINT)

        environmentGroup.check(when(authorization) {
            PRODUCTION_TOKENIZATION_KEY -> R.id.production
            else -> R.id.sandbox
        })

        environmentGroup.setOnCheckedChangeListener { _, checkedId ->
            val intent = getIntent()
            if (checkedId == R.id.production) {
                intent.putExtra(EXTRA_AUTHORIZATION, PRODUCTION_TOKENIZATION_KEY)
                intent.putExtra(EXTRA_ENDPOINT, PRODUCTION_ENDPOINT)
            } else {
                intent.putExtra(EXTRA_AUTHORIZATION, SANDBOX_TOKENIZATION_KEY)
                intent.putExtra(EXTRA_ENDPOINT, SANDBOX_ENDPOINT)
            }

            startActivity(intent)
            finish()
        }

        startBraintree()
    }

    private fun startBraintree() {
        tokenizeButton.isEnabled = false
        transactButton.isEnabled = false

        try {
            braintreeFragment = BraintreeFragment.newInstance(this, authorization)
        } catch (ignored: InvalidArgumentException) {
        }

        SamsungPay.isReadyToPay(braintreeFragment!!, BraintreeResponseListener { availability ->
            when (availability.status) {
                SPAY_READY -> tokenizeButton.isEnabled = true
                SPAY_NOT_READY -> {
                    val reason = availability.reason
                    if (reason == ERROR_SPAY_APP_NEED_TO_UPDATE) {
                        showDialog("Need to update Samsung Pay app...")
                        SamsungPay.goToUpdatePage(braintreeFragment!!)
                    } else if (reason == ERROR_SPAY_SETUP_NOT_COMPLETED) {
                        showDialog("Samsung Pay setup not completed...")
                        SamsungPay.activateSamsungPay(braintreeFragment!!)
                    } else if (reason == SamsungPay.SPAY_NO_SUPPORTED_CARDS_IN_WALLET) {
                        showDialog("No supported cards in wallet")
                    }
                }
                SPAY_NOT_SUPPORTED -> {
                    showDialog("Samsung Pay is not supported")
                    tokenizeButton.isEnabled = false
                }
            }
        })
    }

    fun tokenize(v: View) {
        SamsungPay.createPaymentManager(braintreeFragment!!, BraintreeResponseListener {
            paymentManager = it

            SamsungPay.createPaymentInfo(braintreeFragment!!, BraintreeResponseListener { builder ->
                val paymentInfo = builder
                    .setAddressInPaymentSheet(CustomSheetPaymentInfo.AddressInPaymentSheet.NEED_BILLING_AND_SHIPPING)
                    .setCustomSheet(customSheet)
                    .setOrderNumber("order-number")
                    .build()


                SamsungPay.requestPayment(
                    braintreeFragment!!,
                    paymentManager,
                    paymentInfo,
                    object : SamsungPayCustomTransactionUpdateListener {
                        override fun onSuccess(response: CustomSheetPaymentInfo, extraPaymentData: Bundle) {
                            val custosheet = response.customSheet
                            val billingAddressControl =
                                custosheet.getSheetControl("billingAddressId") as AddressControl
                            val billingAddress = billingAddressControl.address

                            val shippingAddress = response.paymentShippingAddress

                            displayAddresses(billingAddress, shippingAddress)
                        }

                        override fun onCardInfoUpdated(cardInfo: CardInfo, custosheet: CustomSheet) {
                            val amountBoxControl = custosheet.getSheetControl("amountID") as AmountBoxControl
                            amountBoxControl.updateValue("itemId", 1.0)
                            amountBoxControl.updateValue("taxId", 1.0)
                            amountBoxControl.updateValue("shippingId", 1.0)
                            amountBoxControl.updateValue("interestId", 1.0)
                            amountBoxControl.updateValue("fuelId", 1.0)

                            custosheet.updateControl(amountBoxControl)
                        }
                    })
            })
        })
    }

    override fun onError(error: Exception) {
        if (error is SamsungPayException) {
            val (code) = error

            when (code) {
                // Handle SamsungPayException
            }

            showDialog("Samsung Pay failed with error code " + error.code)
        }
    }

    override fun onPaymentMethodNonceCreated(it: PaymentMethodNonce) {
        paymentMethodNonce = it
        transactButton.isEnabled = true

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
            billingAddressDetails.text = TextUtils.join(
                "\n", Arrays.asList(
                    "Billing Address",
                    "Addressee: " + billingAddress.addressee,
                    "AddressLine1: " + billingAddress.addressLine1,
                    "AddressLine2: " + billingAddress.addressLine2,
                    "City: " + billingAddress.city,
                    "PostalCode: " + billingAddress.postalCode,
                    "CountryCode: " + billingAddress.countryCode
                )
            )
        }

        if (shippingAddress != null) {
            shippingAddressDetails.text = TextUtils.join(
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

        nonceDetails.text = display
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

        getApiClient(endpoint)?.createTransaction(
            paymentMethodNonce.nonce, "SamsungPayFD", totalAmount.toString(), callback)
    }

    private fun showDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, which -> dialog.dismiss() }
            .show()
    }

    private fun showSpinner(show: Boolean) {
        val pb = findViewById<ProgressBar>(R.id.progressBar)
        pb.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.INVISIBLE
    }

    private fun <T : View> bind(@IdRes res : Int) : Lazy<T> {
        @Suppress("UNCHECKED_CAST")
        return lazy { findViewById<T>(res) }
    }
}
