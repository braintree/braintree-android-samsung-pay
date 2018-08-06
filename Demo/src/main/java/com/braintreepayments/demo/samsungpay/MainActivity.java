package com.braintreepayments.demo.samsungpay;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.SamsungPay;
import com.braintreepayments.api.SamsungPayAvailability;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.exceptions.SamsungPayException;
import com.braintreepayments.api.interfaces.*;
import com.braintreepayments.api.models.BinData;
import com.braintreepayments.api.models.BraintreeRequestCodes;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.SamsungPayNonce;
import com.braintreepayments.demo.samsungpay.internal.ApiClient;
import com.braintreepayments.demo.samsungpay.models.Transaction;
import com.samsung.android.sdk.samsungpay.v2.SpaySdk;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.*;
import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;

import java.util.Arrays;

import static com.samsung.android.sdk.samsungpay.v2.SpaySdk.*;

public class MainActivity extends AppCompatActivity implements BraintreeErrorListener, BraintreeCancelListener,
        PaymentMethodNonceCreatedListener {
    private static final String EXTRA_AUTHORIZATION = "com.braintreepayments.demo.samsungpay.EXTRA_AUTHORIZATION";
    private static final String EXTRA_ENDPOINT = "com.braintreepayments.demo.samsungpay.EXTRA_ENDPOINT";

    private static final String PRODUCTION_TOKENIZATION_KEY = "production_t2wns2y2_dfy45jdj3dxkmz5m";
    private static final String SANDBOX_TOKENIZATION_KEY = "sandbox_tmxhyf7d_dcpspy2brwdjr3qn";

    private static final String PRODUCTION_ENDPOINT = "https://executive-sample-merchant.herokuapp.com";
    private static final String SANDBOX_ENDPOINT = "https://braintree-sample-merchant.herokuapp.com";

    private RadioGroup mEnvironmentGroup;
    private Button mTokenizeButton;
    private Button mTransactButton;
    private BraintreeFragment mBraintreeFragment;
    private static ApiClient sApiClient;
    private TextView mBillingAddressDetails;
    private TextView mShippingAddressDetails;
    private TextView mNonceDetails;
    private PaymentManager mPaymentManager;
    private PaymentMethodNonce mPaymentMethodNonce;
    private String mAuthorization;
    private String mEndpoint;
    private Double mTotalAmount = 1d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEnvironmentGroup = findViewById(R.id.environment_group);
        mTokenizeButton = findViewById(R.id.samsung_pay_tokenize);
        mTransactButton = findViewById(R.id.samsung_pay_transact);
        mBillingAddressDetails = findViewById(R.id.billing_address_details);
        mShippingAddressDetails = findViewById(R.id.shipping_address_details);
        mNonceDetails = findViewById(R.id.nonce_details);
        mNonceDetails.setVisibility(View.VISIBLE);

        Bundle extras = getIntent().getExtras();
        mAuthorization = extras.getString(EXTRA_AUTHORIZATION, SANDBOX_TOKENIZATION_KEY);
        mEndpoint = extras.getString(EXTRA_ENDPOINT, SANDBOX_ENDPOINT);

        mEnvironmentGroup.check(PRODUCTION_TOKENIZATION_KEY.equals(mAuthorization) ? R.id.production : R.id.sandbox);
        mEnvironmentGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Intent intent = getIntent();
                if (checkedId == R.id.production) {
                    intent.putExtra(EXTRA_AUTHORIZATION, PRODUCTION_TOKENIZATION_KEY);
                    intent.putExtra(EXTRA_ENDPOINT, PRODUCTION_ENDPOINT);
                } else {
                    intent.putExtra(EXTRA_AUTHORIZATION, SANDBOX_TOKENIZATION_KEY);
                    intent.putExtra(EXTRA_ENDPOINT, SANDBOX_ENDPOINT);
                }

                startActivity(intent);
                finish();
            }
        });

        startBraintree();
    }

    private void startBraintree() {
        mTokenizeButton.setEnabled(false);
        mTransactButton.setEnabled(false);

        try {
            mBraintreeFragment = BraintreeFragment.newInstance(this, mAuthorization);
        } catch (InvalidArgumentException ignored) {
        }

        SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<SamsungPayAvailability>() {
            @Override
            public void onResponse(SamsungPayAvailability availability) {
                switch (availability.getStatus()) {
                    case SPAY_READY:
                        mTokenizeButton.setEnabled(true);
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
                        mTokenizeButton.setEnabled(false);
                        break;
                }
            }
        });
    }

    public void tokenize(View v) {
        SamsungPay.createPaymentManager(mBraintreeFragment, new BraintreeResponseListener<PaymentManager>() {
            @Override
            public void onResponse(PaymentManager paymentManager) {
                mPaymentManager = paymentManager;

                SamsungPay.createPaymentInfo(mBraintreeFragment, new BraintreeResponseListener<CustomSheetPaymentInfo.Builder>() {
                    @Override
                    public void onResponse(CustomSheetPaymentInfo.Builder builder) {
                        CustomSheetPaymentInfo paymentInfo = builder
                                .setAddressInPaymentSheet(CustomSheetPaymentInfo.AddressInPaymentSheet.NEED_BILLING_AND_SHIPPING)
                                .setCustomSheet(getCustomSheet())
                                .setOrderNumber("order-number")
                                .build();


                        SamsungPay.requestPayment(mBraintreeFragment, mPaymentManager, paymentInfo, new SamsungPayCustomTransactionUpdateListener() {
                            @Override
                            public void onSuccess(CustomSheetPaymentInfo response, Bundle extraPaymentData) {
                                CustomSheet customSheet = response.getCustomSheet();
                                AddressControl billingAddressControl = (AddressControl) customSheet.getSheetControl("billingAddressId");
                                CustomSheetPaymentInfo.Address billingAddress = billingAddressControl.getAddress();

                                CustomSheetPaymentInfo.Address shippingAddress = response.getPaymentShippingAddress();

                                displayAddresses(billingAddress, shippingAddress);
                            }

                            @Override
                            public void onCardInfoUpdated(@NonNull CardInfo cardInfo, @NonNull CustomSheet customSheet) {
                                AmountBoxControl amountBoxControl = (AmountBoxControl) customSheet.getSheetControl("amountID");
                                mTotalAmount = 2d;
                                amountBoxControl.setAmountTotal(mTotalAmount, AmountConstants.FORMAT_TOTAL_PRICE_ONLY);

                                customSheet.updateControl(amountBoxControl);
                                mPaymentManager.updateSheet(customSheet);
                            }
                        });
                    }
                });
            }
        });
    }

    private CustomSheet getCustomSheet() {
        CustomSheet sheet = new CustomSheet();

        final AddressControl billingAddressControl = new AddressControl("billingAddressId", SheetItemType.BILLING_ADDRESS);
        billingAddressControl.setAddressTitle("Billing Address");
        billingAddressControl.setSheetUpdatedListener(new SheetUpdatedListener() {
            @Override
            public void onResult(String controlId, final CustomSheet customSheet) {
                Log.d("billing sheet updated", controlId);

                mPaymentManager.updateSheet(customSheet);
            }
        });
        sheet.addControl(billingAddressControl);

        final AddressControl shippingAddressControl = new AddressControl("shippingAddressId", SheetItemType.SHIPPING_ADDRESS);
        shippingAddressControl.setAddressTitle("Shipping Address");
        shippingAddressControl.setSheetUpdatedListener(new SheetUpdatedListener() {
            @Override
            public void onResult(String controlId, final CustomSheet customSheet) {
                Log.d("shipping sheet updated", controlId);

                mPaymentManager.updateSheet(customSheet);
            }
        });
        sheet.addControl(shippingAddressControl);

        AmountBoxControl amountBoxControl = new AmountBoxControl("amountID", "USD");
        amountBoxControl.setAmountTotal(mTotalAmount, AmountConstants.FORMAT_TOTAL_PRICE_ONLY);
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
                    break;
            }

            showDialog("Samsung Pay failed with error code " + ((SamsungPayException) error).getCode());
        }
    }

    @Override
    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        mPaymentMethodNonce = paymentMethodNonce;
        mTransactButton.setEnabled(true);

        displayPaymentMethodNonce(paymentMethodNonce);
    }

    public void onCancel(int requestCode) {
        if (requestCode == BraintreeRequestCodes.SAMSUNG_PAY) {
            Log.d("SamsungPay", "User canceled payment.");
        }
    }

    private void displayAddresses(CustomSheetPaymentInfo.Address billingAddress, CustomSheetPaymentInfo.Address shippingAddress) {
        if (billingAddress != null) {
            mBillingAddressDetails.setText(TextUtils.join("\n", Arrays.asList(
                    "Billing Address",
                    "Addressee: " + billingAddress.getAddressee(),
                    "AddressLine1: " + billingAddress.getAddressLine1(),
                    "AddressLine2: " + billingAddress.getAddressLine2(),
                    "City: " + billingAddress.getCity(),
                    "PostalCode: " + billingAddress.getPostalCode(),
                    "CountryCode: " + billingAddress.getCountryCode()
            )));
        }

        if (shippingAddress != null) {
            mShippingAddressDetails.setText(TextUtils.join("\n", Arrays.asList(
                    "Shipping Address",
                    "Addressee: " + shippingAddress.getAddressee(),
                    "AddressLine1: " + shippingAddress.getAddressLine1(),
                    "AddressLine2: " + shippingAddress.getAddressLine2(),
                    "City: " + shippingAddress.getCity(),
                    "PostalCode: " + shippingAddress.getPostalCode(),
                    "CountryCode: " + shippingAddress.getCountryCode()
            )));
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

    public void transact(View v) {
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

        getApiClient(mEndpoint)
                .createTransaction(mPaymentMethodNonce.getNonce(),
                        "SamsungPayFD",
                        mTotalAmount.toString(),
                        callback);
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
        ProgressBar pb = findViewById(R.id.progressBar);
        pb.setVisibility(show ? ProgressBar.VISIBLE : ProgressBar.INVISIBLE);
    }

    static ApiClient getApiClient(String endpoint) {
        class ApiClientRequestInterceptor implements RequestInterceptor {
            @Override
            public void intercept(RequestFacade request) {
                request.addHeader("User-Agent", "braintree/android-demo-app/" + BuildConfig.VERSION_NAME);
                request.addHeader("Accept", "application/json");
            }
        }

        if (sApiClient == null) {
            sApiClient = new RestAdapter.Builder()
                    .setEndpoint(endpoint)
                    .setLogLevel(RestAdapter.LogLevel.FULL)
                    .setRequestInterceptor(new ApiClientRequestInterceptor())
                    .build()
                    .create(ApiClient.class);
        }

        return sApiClient;
    }
}
