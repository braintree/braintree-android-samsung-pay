package com.braintreepayments.demo.samsungpay.internal;

import com.braintreepayments.demo.samsungpay.models.Transaction;
import retrofit.Callback;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.POST;

public interface ApiClient {
    @FormUrlEncoded
    @POST("/nonce/transaction")
    void createTransaction(@Field("nonce") String nonce,
                           @Field("merchant_account_id") String merchantAccountId,
                           Callback<Transaction> callback);
}
