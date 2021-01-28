package com.braintreepayments.api

import android.content.Context
import android.os.Bundle
import com.braintreepayments.api.exceptions.SamsungPayException
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.SamsungPay
import com.samsung.android.sdk.samsungpay.v2.SpaySdk
import com.samsung.android.sdk.samsungpay.v2.SpaySdk.*
import com.samsung.android.sdk.samsungpay.v2.StatusListener
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import org.json.JSONObject
import java.util.*

class SamsungPayAvailability() {
    var status: Int = SPAY_NOT_SUPPORTED
    var reason: Int = 0

    constructor(status: Int, bundle: Bundle?) : this() {
        this.status = status
        if (bundle != null) {
            this.reason = bundle.getInt(SamsungPay.EXTRA_ERROR_REASON)
        }
    }
    constructor(status: Int, reason: Int) : this() {
        this.status = status
        this.reason = reason
    }
}

class SamsungPayClient(private var braintreeClient: BraintreeClient) {

    companion object {
        const val SPAY_NO_SUPPORTED_CARDS_IN_WALLET = -10000
        const val BRAINTREE_TOKENIZATION_API_VERSION = "2018-10-01"
    }

    /**
     * Forwards the user to the Samsung Pay update page.
     * This should be invoked when Samsung Pay returns the [ERROR_SPAY_APP_NEED_TO_UPDATE] result from [isReadyToPay].
     *
     * @param [context] [BraintreeFragment]
     */
    fun goToUpdatePage(context: Context) {
        getPartnerInfo(object : SamsungPayGetPartnerInfoCallback {
            override fun onResult(partnerInfo: BraintreePartnerInfo?, error: Exception?) {
                val samsungPay = partnerInfo?.let { getSamsungPay(context, it) }

                if (samsungPay != null) {
                    samsungPay.goToUpdatePage()
                }
                braintreeClient.sendAnalyticsEvent("samsung-pay.goto-update-page")
            }
        })
    }

    /**
     * Forwards the user to the Samsung Pay activate page.
     * This should be invoked when Samsung Pay returns the [ERROR_SPAY_SETUP_NOT_COMPLETED] result from [isReadyToPay].
     *
     * @param [context] [BraintreeFragment]
     */
    fun activateSamsungPay(context: Context) {
        getPartnerInfo(object : SamsungPayGetPartnerInfoCallback {
            override fun onResult(partnerInfo: BraintreePartnerInfo?, error: Exception?) {
                val samsungPay = partnerInfo?.let { getSamsungPay(context, it) }

                if (samsungPay != null) {
                    samsungPay.activateSamsungPay()
                }
                braintreeClient.sendAnalyticsEvent("samsung-pay.activate-samsung-pay")
            }
        })
    }

    /**
     * Call isReadyToPay before starting your Samsung Pay flow. isReadyToPay will call you back with the
     * status of Samsung Pay. If the Samsung Pay jar has not been included, or if the status of
     * Samsung Pay is anything but [SamsungPayStatus.SPAY_READY], the listener will be called back
     * with a value of false. If the Samsung Pay callback fails and returns an error, that error
     * will be posted to the [BraintreeErrorListener] callback attached to the instance of
     * [BraintreeFragment] passed in here.
     *
     * @param [context] [BraintreeFragment]
     * @param [callback] Callback with [SamsungPayAvailability]. Properties returned are:
     *
     * SPAY_READY - Samsung Pay is ready to handle the payment.
     *
     * SPAY_NOT_READY - Samsung Pay cannot handle the payment currently. See [SamsungPayAvailability.reason] for more
     * details on why Samsung Pay is not ready.
     *
     * SPAY_NOT_SUPPORTED - Samsung Pay is not supported on the current device.
     */
    fun isReadyToPay(context: Context, callback: SamsungPayIsReadyToPayCallback) {
        if (!isSamsungPayAvailable()) {
            callback.onResult(SamsungPayAvailability(SPAY_NOT_SUPPORTED, Bundle()), null)
            braintreeClient.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.samsung-pay-class-unavailable")
            return
        }

        getPartnerInfo(object : SamsungPayGetPartnerInfoCallback {
            override fun onResult(partnerInfo: BraintreePartnerInfo?, error: Exception?) {
                if (partnerInfo != null) {
                    val samsungPay = getSamsungPay(context, partnerInfo)
                    samsungPay.getSamsungPayStatus(object : StatusListener {
                        override fun onSuccess(status: Int, bundle: Bundle) {
                            val samsungPayAvailability = SamsungPayAvailability(status, bundle)

                            if (status != SPAY_READY) {
                                when (status) {
                                    SPAY_NOT_SUPPORTED -> braintreeClient.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.device-not-supported")
                                    SPAY_NOT_READY -> braintreeClient.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.not-ready")
                                }
                                callback.onResult(samsungPayAvailability, null)
                                return
                            }

                            requestCardInfo(context, partnerInfo, object : SamsungPayRequestCardInfoCallback {
                                override fun onResult(cardInfoAvailability: SamsungPayAvailability?, error: Exception?) {
                                    error?.let {
                                        callback.onResult(null, error)
                                        return
                                    }
                                    val availability = cardInfoAvailability
                                            ?: samsungPayAvailability

                                    if (availability.status == SPAY_READY) {
                                        braintreeClient.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.ready")
                                    }

                                    callback.onResult(availability, null)
                                }
                            })
                        }

                        override fun onFail(errorCode: Int, bundle: Bundle) {
                            callback.onResult(null, SamsungPayException(errorCode, bundle))
                            braintreeClient.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.failed")
                        }
                    })
                }
            }
        })
    }

    // Returns SamsungPayAvailability iff there are no supported cards in the Samsung Pay app, otherwise returns null.
    private fun requestCardInfo(
            context: Context,
            braintreePartnerInfo: BraintreePartnerInfo,
            callback: SamsungPayRequestCardInfoCallback
    ) {
        val paymentManager = getPaymentManager(context, braintreePartnerInfo)
        paymentManager.requestCardInfo(Bundle(), object : PaymentManager.CardInfoListener {
            override fun onResult(cardResponse: MutableList<CardInfo>?) {
                if (cardResponse == null) {
                    callback.onResult(SamsungPayAvailability(SPAY_NOT_READY, SPAY_NO_SUPPORTED_CARDS_IN_WALLET), null)
                    braintreeClient.sendAnalyticsEvent("samsung-pay.request-card-info.no-supported-cards-in-wallet")
                    return
                }

                val acceptedCardBrands =
                        getAcceptedCardBrands(braintreePartnerInfo.configuration.supportedCardBrands)
                val customerCardBrands = cardResponse.map { response -> response.brand }
                if (customerCardBrands.intersect(acceptedCardBrands).isEmpty()) {
                    callback.onResult(SamsungPayAvailability(SPAY_NOT_READY, SPAY_NO_SUPPORTED_CARDS_IN_WALLET), null)
                    braintreeClient.sendAnalyticsEvent("samsung-pay.request-card-info.no-supported-cards-in-wallet")
                    return
                }

                callback.onResult(null, null)
            }

            override fun onFailure(errorCode: Int, bundle: Bundle?) {
                callback.onResult(null, SamsungPayException(errorCode, bundle))
                braintreeClient.sendAnalyticsEvent("samsung-pay.request-card-info.failed")
            }
        })
    }

    /**
     * Creates a [CustomSheetPaymentInfo.Builder] with Braintree properties such as the merchant ID, merchant name,
     * and allowed card brands.
     *
     * The builder returned from the [BraintreeResponseListener] can be used to set merchant properties such as the
     * custom sheet, address requirements, and etc.
     *
     * @param [fragment] [BraintreeFragment]
     * @param [listener] Returns a [CustomSheetPaymentInfo.Builder] that can be modified
     * for the merchant's requirements.
     */
    fun createPaymentInfo(
            listener: BraintreeResponseListener<CustomSheetPaymentInfo.Builder>
    ) {
        getPartnerInfo(object : SamsungPayGetPartnerInfoCallback {
            override fun onResult(partnerInfo: BraintreePartnerInfo?, error: Exception?) {
                partnerInfo?.let {
                    val paymentInfo = CustomSheetPaymentInfo.Builder()
                            .setMerchantName(partnerInfo.configuration.merchantDisplayName)
                            .setMerchantId(partnerInfo.configuration.samsungAuthorization)
                            .setAllowedCardBrands(getAcceptedCardBrands(partnerInfo.configuration.supportedCardBrands))
                    listener.onResponse(paymentInfo)
                    braintreeClient.sendAnalyticsEvent("samsung-pay.create-payment-info.success")
                }
            }
        })
    }

    /**
     * Creates a [PaymentManager] instance that can communicate with Braintree.
     *
     * This instance should be used to update Samsung Pay's custom sheets and sheet controls.
     *
     * @param [fragment] [BraintreeFragment]
     * @param [callback] Returns the [PaymentManager] instance.
     */
    fun createPaymentManager(
            context: Context,
            callback: SamsungPayCreatePaymentManagerCallback
    ) {
        getPartnerInfo(object : SamsungPayGetPartnerInfoCallback {
            override fun onResult(partnerInfo: BraintreePartnerInfo?, error: Exception?) {
                val paymentManager = partnerInfo?.let { getPaymentManager(context, it) }
                callback.onResult(paymentManager, null)
                braintreeClient.sendAnalyticsEvent("samsung-pay.create-payment-manager.success")
            }
        })
    }

    /**
     * Takes a [CustomSheetInfo.Builder] and starts the Samsung Pay flow with some custom sheet provided.
     *
     * @param [fragment] [BraintreeFragment]
     * @param [paymentManager] [PaymentManager] returned from [createPaymentManager].
     * Used to update the Samsung Pay custom sheets.
     * @param [customSheetPaymentInfo] The [CustomSheetPaymentInfo] returned by [createPaymentInfo], and modified for the
     * merchant's extra requirements.
     * @param [listener] [SamsungPayCustomTransactionUpdateListener]. Contains two methods to listen to:
     *
     * [SamsungPayCustomTransactionUpdateListener.onSuccess] which gets called when the Samsung Pay flow succeeded.
     *
     * [SamsungPayCustomTransactionUpdateListener.onCardInfoUpdated] which gets called when the customer selects
     * a different card payment method. This call must complete with a call to [PaymentManager.updateSheet] or one of the
     * alternatives.
     */
    fun requestPayment(
            paymentManager: PaymentManager,
            customSheetPaymentInfo: CustomSheetPaymentInfo,
            listener: SamsungPayCustomTransactionUpdateListener,
            callback: SamsungPayTransactionCallback
    ) {
        paymentManager.startInAppPayWithCustomSheet(
                customSheetPaymentInfo,
                SamsungPayCustomTransactionListenerWrapper(paymentManager, listener, braintreeClient, object : SamsungPayTransactionCallback {
                    override fun onResult(samsungPayNonce: SamsungPayNonce?, error: Exception?) {
                        // TODO: handle
                        callback.onResult(samsungPayNonce, error)
                    }
                })
        )
    }

    /**
     * @return true if the SamsungPay SDK is available in the classpath, i.e. you have included
     * the Samsung Pay jar file in your declared app dependencies.
     */
    fun isSamsungPayAvailable(): Boolean {
        return ClassHelper.isClassAvailable("com.samsung.android.sdk.samsungpay.v2.SamsungPay")
    }

    private fun getAcceptedCardBrands(configurationBrands: Set<String>): List<SpaySdk.Brand> {
        val samsungAcceptedList = ArrayList<SpaySdk.Brand>()

        for (braintreeAcceptedCardBrand in configurationBrands) {
            when (braintreeAcceptedCardBrand.toLowerCase()) {
                "visa" -> samsungAcceptedList.add(SpaySdk.Brand.VISA)
                "mastercard" -> samsungAcceptedList.add(SpaySdk.Brand.MASTERCARD)
                "discover" -> samsungAcceptedList.add(SpaySdk.Brand.DISCOVER)
                "american_express" -> samsungAcceptedList.add(SpaySdk.Brand.AMERICANEXPRESS)
            }
        }

        return samsungAcceptedList
    }

    private fun getPartnerInfo(
            callback: SamsungPayGetPartnerInfoCallback
    ) {
        braintreeClient.getConfiguration() { configuration, error ->
            configuration?.let {
                val bundle = Bundle()

                bundle.putString(PARTNER_SERVICE_TYPE, ServiceType.INAPP_PAYMENT.toString())
                bundle.putBoolean(
                        PaymentManager.EXTRA_KEY_TEST_MODE,
                        configuration.samsungPay.environment.toUpperCase() == "SANDBOX"
                )

                val additionalData = JSONObject()
                additionalData.put("braintreeTokenizationApiVersion", BRAINTREE_TOKENIZATION_API_VERSION)
                additionalData.put(
                        "clientSdkMetadata", MetadataBuilder()
                        .integration(braintreeClient.integrationType)
                        .sessionId(braintreeClient.sessionId)
                        .version()
                        .build()
                )

                bundle.putString("additionalData", additionalData.toString())

                callback.onResult(BraintreePartnerInfo(configuration.samsungPay, bundle), null)
            }
        }
    }

    private fun getSamsungPay(context: Context, info: PartnerInfo): SamsungPay {
        return SamsungPay(context, info)
    }

    private fun getPaymentManager(context: Context, info: PartnerInfo): PaymentManager {
        return PaymentManager(context, info)
    }
}
