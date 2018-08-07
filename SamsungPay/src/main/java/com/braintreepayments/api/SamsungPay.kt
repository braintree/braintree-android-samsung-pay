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

        @JvmStatic
        fun goToUpdatePage(fragment: BraintreeFragment) {
            getPartnerInfo(fragment, BraintreeResponseListener { braintreePartnerInfo ->
                val samsungPay = getSamsungPay(fragment, braintreePartnerInfo)

                samsungPay.goToUpdatePage()
                fragment.sendAnalyticsEvent("samsung-pay.goto-update-page")
            })
        }

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
         * TODO(Modify this s.t. the response listener also passes the reason for readiness, so that the merchant can take available actions)
         *
         * @param [fragment] TODO
         * @param [listener] TODO
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
         * Creates a {@link CustomSheetPaymentInfo.Builder} with the merchant ID, merchant name, and allowed
         * card brands populated by the Braintree merchant account configuration.
         * @param fragment - {@link BraintreeFragment}
         * @param listener {@link BraintreeResponseListener<CustomSheetPaymentInfo.Builder>} - listens for
         * the Braintree flavored {@link CustomSheetPaymentInfo.Builder}.
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
         * Creates a [PaymentManager] that can communicate with Braintree.
         * @param [fragment] [BraintreeFragment]
         * @param [listener] Returns the [PaymentManager]
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
         * [requestPayment] takes a CustomSheetInfo.Builder and starts intitiates the Samsung Pay flow
         * with some custom UI provided by you.
         *
         * @param [fragment] TODO
         * @param [customSheetPaymentInfo] TODO
         * @param [listener] TODO
         */
        @JvmStatic
        fun requestPayment(
            fragment: BraintreeFragment,
            customSheetPaymentInfo: CustomSheetPaymentInfo,
            listener: SamsungPayCustomTransactionUpdateListener
        ) {
            createPaymentManager(fragment, BraintreeResponseListener {
                requestPayment(fragment, it, customSheetPaymentInfo, listener)
            })
        }

        /**
         * [requestPayment] takes a CustomSheetInfo.Builder and starts intitiates the Samsung Pay flow
         * with some custom UI provided by you.
         *
         * @param [fragment] TODO
         * @param [paymentManager] TODO
         * @param [customSheetPaymentInfo] TODO
         * @param [listener] TODO
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

                val clientSdkMetadataJson = JSONObject()
                clientSdkMetadataJson.put(
                    "clientSdkMetadata", MetadataBuilder()
                        .integration(fragment.integrationType)
                        .sessionId(fragment.sessionId)
                        .version()
                        .build()
                )
                bundle.putString("additionalData", clientSdkMetadataJson.toString())

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
