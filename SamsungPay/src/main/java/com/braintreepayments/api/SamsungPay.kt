package com.braintreepayments.api

import android.os.Bundle
import com.braintreepayments.api.exceptions.SamsungPayException
import com.braintreepayments.api.interfaces.BraintreeErrorListener
import com.braintreepayments.api.interfaces.BraintreeResponseListener
import com.braintreepayments.api.interfaces.SamsungPayCustomTransactionUpdateListener
import com.braintreepayments.api.internal.ClassHelper
import com.braintreepayments.api.models.MetadataBuilder
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

class SamsungPay {
    companion object {
        const val SPAY_NO_SUPPORTED_CARDS_IN_WALLET = -10000

        /**
         * Forwards the user to the Samsung Pay update page.
         * This should be invoked when Samsung Pay returns the [ERROR_SPAY_APP_NEED_TO_UPDATE] result from [isReadyToPay].
         *
         * @param [fragment] [BraintreeFragment]
         */
        @JvmStatic
        fun goToUpdatePage(fragment: BraintreeFragment) {
            getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
                val samsungPay = getSamsungPay(fragment, braintreePartnerInfo)

                samsungPay.goToUpdatePage()
                fragment.sendAnalyticsEvent("samsung-pay.goto-update-page")
            })
        }

        /**
         * Forwards the user to the Samsung Pay activate page.
         * This should be invoked when Samsung Pay returns the [ERROR_SPAY_SETUP_NOT_COMPLETED] result from [isReadyToPay].
         *
         * @param [fragment] [BraintreeFragment]
         */
        @JvmStatic
        fun activateSamsungPay(fragment: BraintreeFragment) {
            getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
                val samsungPay = getSamsungPay(fragment, braintreePartnerInfo)

                samsungPay.activateSamsungPay()
                fragment.sendAnalyticsEvent("samsung-pay.activate-samsung-pay")
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
         * @param [fragment] [BraintreeFragment]
         * @param [listener] Callback with [SamsungPayAvailability]. Properties returned are:
         *
         * SPAY_READY - Samsung Pay is ready to handle the payment.
         *
         * SPAY_NOT_READY - Samsung Pay cannot handle the payment currently. See [SamsungPayAvailability.reason] for more
         * details on why Samsung Pay is not ready.
         *
         * SPAY_NOT_SUPPORTED - Samsung Pay is not supported on the current device.
         */
        @JvmStatic
        fun isReadyToPay(fragment: BraintreeFragment, listener: BraintreeResponseListener<SamsungPayAvailability>) {
            if (!isSamsungPayAvailable()) {
                listener.onResponse(SamsungPayAvailability(SPAY_NOT_SUPPORTED, Bundle()))
                fragment.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.samsung-pay-class-unavailable")
                return
            }

            getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
                val samsungPay = getSamsungPay(fragment, braintreePartnerInfo)

                samsungPay.getSamsungPayStatus(object : StatusListener {
                    override fun onSuccess(status: Int, bundle: Bundle) {
                        val samsungPayAvailability = SamsungPayAvailability(status, bundle)

                        if (status != SPAY_READY) {
                            when (status) {
                                SPAY_NOT_SUPPORTED -> fragment.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.device-not-supported")
                                SPAY_NOT_READY -> fragment.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.not-ready")
                            }
                            listener.onResponse(samsungPayAvailability)
                            return
                        }

                        requestCardInfo(
                            fragment,
                            braintreePartnerInfo,
                            BraintreeResponseListener { cardInfoAvailability ->
                                val availability = cardInfoAvailability ?: samsungPayAvailability

                                if (availability.status == SPAY_READY) {
                                    fragment.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.ready")
                                }

                                listener.onResponse(availability)
                            })
                    }

                    override fun onFail(errorCode: Int, bundle: Bundle) {
                        fragment.postCallback(SamsungPayException(errorCode, bundle))
                        fragment.sendAnalyticsEvent("samsung-pay.is-ready-to-pay.failed")
                    }
                })
            })
        }

        // Returns SamsungPayAvailability iff there are no supported cards in the Samsung Pay app, otherwise returns null.
        @JvmStatic
        private fun requestCardInfo(
            fragment: BraintreeFragment,
            braintreePartnerInfo: BraintreePartnerInfo,
            listener: BraintreeResponseListener<SamsungPayAvailability?>
        ) {
            val paymentManager = getPaymentManager(fragment, braintreePartnerInfo)
            paymentManager.requestCardInfo(Bundle(), object : PaymentManager.CardInfoListener {
                override fun onResult(cardResponse: MutableList<CardInfo>?) {
                    if (cardResponse == null) {
                        listener.onResponse(SamsungPayAvailability(SPAY_NOT_READY, SPAY_NO_SUPPORTED_CARDS_IN_WALLET))
                        fragment.sendAnalyticsEvent("samsung-pay.request-card-info.no-supported-cards-in-wallet")
                        return
                    }

                    val acceptedCardBrands =
                        getAcceptedCardBrands(braintreePartnerInfo.configuration.supportedCardBrands)
                    val customerCardBrands = cardResponse.map { response -> response.brand }
                    if (customerCardBrands.intersect(acceptedCardBrands).isEmpty()) {
                        listener.onResponse(SamsungPayAvailability(SPAY_NOT_READY, SPAY_NO_SUPPORTED_CARDS_IN_WALLET))
                        fragment.sendAnalyticsEvent("samsung-pay.request-card-info.no-supported-cards-in-wallet")
                        return
                    }

                    listener.onResponse(null)
                }

                override fun onFailure(errorCode: Int, bundle: Bundle?) {
                    fragment.postCallback(SamsungPayException(errorCode, bundle))
                    fragment.sendAnalyticsEvent("samsung-pay.request-card-info.failed")
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
        @JvmStatic
        fun createPaymentInfo(
            fragment: BraintreeFragment,
            listener: BraintreeResponseListener<CustomSheetPaymentInfo.Builder>
        ) {
            getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
                val paymentInfo = CustomSheetPaymentInfo.Builder()
                    .setMerchantName(braintreePartnerInfo.configuration.merchantDisplayName)
                    .setMerchantId(braintreePartnerInfo.configuration.samsungAuthorization)
                    .setAllowedCardBrands(getAcceptedCardBrands(braintreePartnerInfo.configuration.supportedCardBrands))
                listener.onResponse(paymentInfo)
                fragment.sendAnalyticsEvent("samsung-pay.create-payment-info.success")
            })
        }

        /**
         * Creates a [PaymentManager] instance that can communicate with Braintree.
         *
         * This instance should be used to update Samsung Pay's custom sheets and sheet controls.
         *
         * @param [fragment] [BraintreeFragment]
         * @param [listener] Returns the [PaymentManager] instance.
         */
        @JvmStatic
        fun createPaymentManager(
            fragment: BraintreeFragment,
            listener: BraintreeResponseListener<PaymentManager>
        ) {
            getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
                val paymentManager = getPaymentManager(fragment, braintreePartnerInfo)
                listener.onResponse(paymentManager)
                fragment.sendAnalyticsEvent("samsung-pay.create-payment-manager.success")
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
        @JvmStatic
        fun requestPayment(
            fragment: BraintreeFragment,
            paymentManager: PaymentManager,
            customSheetPaymentInfo: CustomSheetPaymentInfo,
            listener: SamsungPayCustomTransactionUpdateListener
        ) {
            paymentManager.startInAppPayWithCustomSheet(
                customSheetPaymentInfo,
                SamsungPayCustomTransactionListenerWrapper(fragment, paymentManager, listener)
            )
        }

        /**
         * @return true if the SamsungPay SDK is available in the classpath, i.e. you have included
         * the Samsung Pay jar file in your declared app dependencies.
         */
        @JvmStatic
        fun isSamsungPayAvailable(): Boolean {
            return ClassHelper.isClassAvailable("com.samsung.android.sdk.samsungpay.v2.SamsungPay")
        }

        @JvmStatic
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

        @JvmStatic
        private fun getPartnerInfo(
            fragment: BraintreeFragment,
            listener: BraintreeResponseListener<BraintreePartnerInfo>
        ) {
            fragment.waitForConfiguration { configuration ->
                val bundle = Bundle()

                bundle.putString(PARTNER_SERVICE_TYPE, ServiceType.INAPP_PAYMENT.toString())
                bundle.putBoolean(
                    PaymentManager.EXTRA_KEY_TEST_MODE,
                    configuration.samsungPay.environment.toUpperCase() == "SANDBOX"
                )

                val additionalData = JSONObject()
                additionalData.put(
                    "clientSdkMetadata", MetadataBuilder()
                        .integration(fragment.integrationType)
                        .sessionId(fragment.sessionId)
                        .version()
                        .build()
                )

                bundle.putString("additionalData", additionalData.toString())

                listener.onResponse(BraintreePartnerInfo(configuration.samsungPay, bundle))
            }
        }

        @JvmStatic
        private fun getSamsungPay(fragment: BraintreeFragment, info: PartnerInfo): SamsungPay {
            return SamsungPay(fragment.activity, info)
        }

        @JvmStatic
        private fun getPaymentManager(fragment: BraintreeFragment, info: PartnerInfo): PaymentManager {
            return PaymentManager(fragment.activity, info)
        }
    }
}
