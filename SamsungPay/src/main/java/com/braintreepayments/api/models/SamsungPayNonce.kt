package com.braintreepayments.api.models

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import org.json.JSONObject

/**
 * {@link PaymentMethodNonce} representing a Samsung Pay card.
 * @see PaymentMethodNonce
 */
public class SamsungPayNonce() : PaymentMethodNonce() {
    var binData: BinData? = null
    var cardType: String? = null
    var sourceCardLast4: String? = null

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<SamsungPayNonce> {
            override fun createFromParcel(parcel: Parcel) = SamsungPayNonce(parcel)
            override fun newArray(size: Int) = arrayOfNulls<SamsungPayNonce>(size)
        }
        fun fromPaymentData(data: String) : SamsungPayNonce {
            val nonce = SamsungPayNonce()
            nonce.from(JSONObject(data))
            return nonce
        }
    }

    // Can't override fromJson and use that because the super implementation does not support
    // Tokenizer's JSON schema
    private fun from(json: JSONObject?) {
        val braintreeDataJson = JSONObject(JSONObject(json?.getString("data")).getString("data"))
        val paymentMethod = braintreeDataJson.getJSONObject("tokenizeSamsungPayCard")
            .getJSONObject("paymentMethod")
        val details = paymentMethod.getJSONObject("details")

        mNonce = paymentMethod.getString("id")
        cardType = details.getString("brand")
        sourceCardLast4 = details.getString("last4")
        mDescription = "ending in $sourceCardLast4"

        // This is a hack to get around the mismatch between the GraphQL API version used in
        // the Braintree Android SDK and the API version used by Samsung to tokenize. Samsung's
        // response has `UNKNOWN` whereas the Braintree SDK expects `Unknown`.
        var formattedBinData = details.getJSONObject("binData")
                .toString()
                .replace("UNKNOWN", "Unknown")
                .replace("YES", "Yes")
                .replace("NO", "No")
        binData = BinData.fromJson(JSONObject(formattedBinData))
    }

    override fun getTypeLabel(): String {
        return "Samsung Pay"
    }

    private constructor(parcel: Parcel) : this() {
        cardType = parcel.readString()
        sourceCardLast4 = parcel.readString()
        binData = parcel.readParcelable<BinData>(BinData::class.java.classLoader)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(cardType)
        parcel.writeString(sourceCardLast4)
        parcel.writeParcelable(binData, flags)
   }
}