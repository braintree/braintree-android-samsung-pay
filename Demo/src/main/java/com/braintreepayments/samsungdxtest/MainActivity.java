package com.braintreepayments.samsungdxtest;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.SamsungCustomSheetTransactionListener;
import com.braintreepayments.api.SamsungPay;
import com.braintreepayments.api.SamsungPayTransactionListener;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AddressControl;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AmountBoxControl;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AmountConstants;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.SheetItemType;
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.SheetUpdatedListener;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SamsungPayTransactionListener {

	private static final String TOKENIZATION_KEY = "sandbox_tmxhyf7d_dcpspy2brwdjr3qn";

	private Button mNormalSheetSamsungPayButton;
	private Button mCustomSheetSamsungPayButton;
	private BraintreeFragment mBraintreeFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mNormalSheetSamsungPayButton = findViewById(R.id.samsung_pay_demo_launch_button_normal_sheet);
		mCustomSheetSamsungPayButton = findViewById(R.id.samsung_pay_demo_launch_button_custom_sheet);

		try {
			mBraintreeFragment = BraintreeFragment.newInstance(this, TOKENIZATION_KEY);
		} catch (InvalidArgumentException ignored) { }
	}

	@Override
	protected void onResume() {
		super.onResume();

		mNormalSheetSamsungPayButton.setOnClickListener(this);
		mCustomSheetSamsungPayButton.setOnClickListener(this);

		SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean isReady) {
				mNormalSheetSamsungPayButton.setEnabled(isReady);
				mCustomSheetSamsungPayButton.setEnabled(isReady);
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();

		mNormalSheetSamsungPayButton.setOnClickListener(null);
		mCustomSheetSamsungPayButton.setOnClickListener(null);
	}

	@Override
	public void onClick(View v) {
		if (v == mNormalSheetSamsungPayButton) {
			SamsungPay.startSamsungPay(mBraintreeFragment, makeBuilder(), this);
		} else if (v == mCustomSheetSamsungPayButton) {
			AddressControl billingAddressControl = new AddressControl("billingAddressId", SheetItemType.BILLING_ADDRESS);

			billingAddressControl.setAddressTitle("Billing Address [control]");
			billingAddressControl.setSheetUpdatedListener(new SheetUpdatedListener() {
				@Override
				public void onResult(String s, CustomSheet customSheet) {
					Log.d("address sheet updated", s);
				}
			});

			final AmountBoxControl amountBoxControl = new AmountBoxControl("amountID", "USD");
			amountBoxControl.addItem("itemId", "Items", 1000, "");
			amountBoxControl.addItem("taxId", "Tax", 50, "");
			amountBoxControl.addItem("shippingId", "Shipping", 10, "");
			amountBoxControl.addItem("interestId", "Interest [ex]", 0, "");
			amountBoxControl.setAmountTotal(1050 + amountBoxControl.getValue("shippingId") + amountBoxControl.getValue("interestId"), AmountConstants.FORMAT_TOTAL_PRICE_ONLY);
			amountBoxControl.addItem(3, "fuelId", "FUEL", 0, "Pending");

			CustomSheet sheet = new CustomSheet();
			sheet.addControl(billingAddressControl);
			sheet.addControl(amountBoxControl);

			CustomSheetPaymentInfo.Builder customSheetInfo = new CustomSheetPaymentInfo.Builder()
					.setCustomSheet(sheet);

			SamsungPay.startSamsungPay(mBraintreeFragment, customSheetInfo, new SamsungCustomSheetTransactionListener() {
				@Override
				public void onCardInfoUpdated(@NonNull CardInfo cardInfo, @NonNull CustomSheet customSheet) {
					amountBoxControl.updateValue("itemId", 1000);
					amountBoxControl.updateValue("taxId", 50);
					amountBoxControl.updateValue("shippingId", 10);
					amountBoxControl.updateValue("interestId", 0);
					amountBoxControl.updateValue("fuelId", 10);

					customSheet.updateControl(amountBoxControl);
				}
			});
		}
	}

	@Nullable
	@Override
	public PaymentInfo.Amount onAddressUpdated(@NonNull PaymentInfo paymentInfo) {
		return null;
	}

	@Nullable
	@Override
	public PaymentInfo.Amount onCardInfoUpdated(@NonNull CardInfo cardInfo) {
		return null;
	}

	private PaymentInfo.Builder makeBuilder() {
		return new PaymentInfo.Builder()
				.setAmount(new PaymentInfo.Amount.Builder()
				.setCurrencyCode("USD")
				.setItemTotalPrice("1")
				.setShippingPrice("0")
				.setTax("0")
				.setTotalPrice("1")
				.build());
	}
}
