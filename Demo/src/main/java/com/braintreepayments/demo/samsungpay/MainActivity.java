package com.braintreepayments.demo.samsungpay;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.SamsungPay;
import com.braintreepayments.api.SamsungPayAvailability;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.exceptions.SamsungPayException;
import com.braintreepayments.api.interfaces.*;
import com.braintreepayments.api.models.BinData;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.SamsungPayNonce;
import com.braintreepayments.demo.samsungpay.internal.ApiClient;
import com.braintreepayments.demo.samsungpay.models.Transaction;
import com.samsung.android.sdk.samsungpay.v2.SpaySdk;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.*;
import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;

import static com.samsung.android.sdk.samsungpay.v2.SpaySdk.*;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, BraintreeErrorListener,
        BraintreeCancelListener, PaymentMethodNonceCreatedListener {

    private static final String PRODUCTION_TOKENIZATION_KEY = "production_t2wns2y2_dfy45jdj3dxkmz5m";
    private static final String SANDBOX_TOKENIZATION_KEY = "sandbox_tmxhyf7d_dcpspy2brwdjr3qn";

    private Button mCustomSheetSamsungPayButton;
    private BraintreeFragment mBraintreeFragment;
    private static ApiClient sApiClient;
    private TextView mNonceDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCustomSheetSamsungPayButton = findViewById(R.id.samsung_pay_demo_launch_button_custom_sheet);
        mNonceDetails = findViewById(R.id.nonce_details);
        mNonceDetails.setVisibility(View.VISIBLE);

        try {
            mBraintreeFragment = BraintreeFragment.newInstance(this, SANDBOX_TOKENIZATION_KEY);
        } catch (InvalidArgumentException ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mCustomSheetSamsungPayButton.setOnClickListener(this);

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                switch (availability.getStatus()) {
                    case SPAY_READY:
                        mCustomSheetSamsungPayButton.setEnabled(true);
                        break;
                    case SPAY_NOT_READY:
                        Integer reason = availability.getReason();
                        if (reason == ERROR_SPAY_APP_NEED_TO_UPDATE) {
                            showDialog("Need to update Samsung Pay app...");
                            SamsungPay.goToUpdatePage(mBraintreeFragment);
                        } else if (reason == ERROR_SPAY_SETUP_NOT_COMPLETED) {
                            showDialog("Samsung Pay setup not completed...");
                            SamsungPay.activateSamsungPay(mBraintreeFragment);
                        } else if (reason == SamsungPay.SPAY_NO_SUPPORTED_CARDS_IN_WALLET) {
                            showDialog("No supported cards in wallet");
                        }
                        break;
                    case SPAY_NOT_SUPPORTED:
                        showDialog("Samsung Pay is not supported");
                        mCustomSheetSamsungPayButton.setEnabled(false);
                        break;
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        mCustomSheetSamsungPayButton.setOnClickListener(null);
    }

    @Override
    public void onClick(View v) {
        SamsungPay.createPaymentInfo(mBraintreeFragment, new BraintreeResponseListener<CustomSheetPaymentInfo.Builder>() {
            @Override
            public void onResponse(CustomSheetPaymentInfo.Builder builder) {
                builder.setCustomSheet(getCustomSheet());
                builder.setOrderNumber("order-number");

                SamsungPay.requestPayment(mBraintreeFragment, builder.build(), new SamsungPayCustomTransactionUpdateListener() {
                    @Override
                    public void onCardInfoUpdated(@NonNull CardInfo cardInfo, @NonNull CustomSheet customSheet) {
                        AmountBoxControl amountBoxControl = (AmountBoxControl) customSheet.getSheetControl("amountID");
                        amountBoxControl.updateValue("itemId", 1);
                        amountBoxControl.updateValue("taxId", 1);
                        amountBoxControl.updateValue("shippingId", 1);
                        amountBoxControl.updateValue("interestId",1);
                        amountBoxControl.updateValue("fuelId", 1);

                        customSheet.updateControl(amountBoxControl);
                    }
                });
            }
        });
    }

    private CustomSheet getCustomSheet() {
        AddressControl billingAddressControl = new AddressControl("billingAddressId", SheetItemType.BILLING_ADDRESS);

        billingAddressControl.setAddressTitle("Billing Address [control]");
        billingAddressControl.setSheetUpdatedListener(new SheetUpdatedListener() {
            @Override
            public void onResult(String s, CustomSheet customSheet) {
                Log.d("address sheet updated", s);
            }
        });

        final AmountBoxControl amountBoxControl = new AmountBoxControl("amountID", "USD");
        amountBoxControl.addItem("itemId", "Items", 1, "");
        amountBoxControl.addItem("taxId", "Tax", 1, "");
        amountBoxControl.addItem("shippingId", "Shipping", 10, "");
        amountBoxControl.addItem("interestId", "Interest [ex]", 0, "");
        amountBoxControl.setAmountTotal(1, AmountConstants.FORMAT_TOTAL_PRICE_ONLY);
        amountBoxControl.addItem(3, "fuelId", "FUEL", 0, "Pending");

        CustomSheet sheet = new CustomSheet();
        sheet.addControl(billingAddressControl);
        sheet.addControl(amountBoxControl);
        return sheet;
    }

    @Override
    public void onError(Exception error) {
        if (error instanceof SamsungPayException) {
            SamsungPayException samsungPayException = (SamsungPayException) error;

            switch (samsungPayException.getCode()) {
                case SpaySdk.ERROR_NO_NETWORK:
                    // handle accordingly
                    // ...
            }

            showDialog("Samsung Pay failed with error code " + ((SamsungPayException) error).getCode());
        }
    }

    @Override
    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        displayPaymentMethodNonce(paymentMethodNonce);
        sendNonceToServer(paymentMethodNonce);
    }

    public void onCancel(int requestCode) {
        if (requestCode == 13595) {
//        if (requestCode == BraintreeRequestCodes.SAMSUNG_PAY) { // TODO
            Log.d("SamsungPay", "User canceled payment.");
        }
    }

    private void displayPaymentMethodNonce(PaymentMethodNonce paymentMethodNonce) {
        SamsungPayNonce nonce = (SamsungPayNonce) paymentMethodNonce;
        String display = nonce.getCardType() + " " + nonce.getDescription() + "\n" +
                "Token: " + nonce.getNonce() + "\n" +
                binDataString(nonce.getBinData());

        mNonceDetails.setText(display);
    }

    private String binDataString(BinData binData) {
        return "Bin Data: \n"  +
                "         - Prepaid: " + binData.getHealthcare() + "\n" +
                "         - Healthcare: " + binData.getHealthcare() + "\n" +
                "         - Debit: " + binData.getDebit() + "\n" +
                "         - Durbin Regulated: " + binData.getDurbinRegulated() + "\n" +
                "         - Commercial: " + binData.getCommercial() + "\n" +
                "         - Payroll: " + binData.getPayroll() + "\n" +
                "         - Issuing Bank: " + binData.getIssuingBank() + "\n" +
                "         - Country of Issuance: " + binData.getCountryOfIssuance() + "\n" +
                "         - Product Id: " + binData.getProductId();
    }

    private void sendNonceToServer(PaymentMethodNonce nonce) {
        showSpinner(true);

        Callback<Transaction> callback = new Callback<Transaction>() {
            @Override
            public void success(Transaction transaction, Response response) {
                showSpinner(false);

                if (transaction.getMessage() != null &&
                        transaction.getMessage().startsWith("created")) {
                    showDialog("Successful transaction: " + transaction.getMessage());
                } else {
                    if (TextUtils.isEmpty(transaction.getMessage())) {
                        showDialog("Server response was empty or malformed");
                    } else {
                        showDialog("Error creating transaction: " + transaction.getMessage());
                    }
                }
            }

            @Override
            public void failure(RetrofitError error) {
                showSpinner(false);

                showDialog("Unable to create a transaction. Response Code: " +
                        error.getResponse().getStatus() + " Response body: " +
                        error.getResponse().getBody());
            }
        };

        getApiClient().createTransaction(nonce.getNonce(), "stch2nfdfwszytw5", callback);
    }

    protected void showDialog(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    protected void showSpinner(boolean show) {
        ProgressBar pb = (ProgressBar) findViewById(R.id.progressBar);
        pb.setVisibility(show ? ProgressBar.VISIBLE : ProgressBar.INVISIBLE);
    }

    static ApiClient getApiClient() {
        class ApiClientRequestInterceptor implements RequestInterceptor {
            @Override
            public void intercept(RequestFacade request) {
                request.addHeader("User-Agent", "braintree/android-demo-app/" + BuildConfig.VERSION_NAME);
                request.addHeader("Accept", "application/json");
            }
        }

        if (sApiClient == null) {
            sApiClient = new RestAdapter.Builder()
                    .setEndpoint("https://braintree-sample-merchant.herokuapp.com")
                    .setRequestInterceptor(new ApiClientRequestInterceptor())
                    .build()
                    .create(ApiClient.class);
        }

        return sApiClient;
    }
}
