package com.braintreepayments.samsungdxtest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.SamsungPay;
import com.braintreepayments.api.SamsungPayTransactionListener;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.interfaces.BraintreeResponseListener;
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo;
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SamsungPayTransactionListener {

	private static final String TOKENIZATION_KEY = "sandbox_tmxhyf7d_dcpspy2brwdjr3qn";

	private Button mSamsungPayButton;
	private BraintreeFragment mBraintreeFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mSamsungPayButton = findViewById(R.id.samsung_pay_demo_launch_button);

		try {
			mBraintreeFragment = BraintreeFragment.newInstance(this, TOKENIZATION_KEY);
		} catch (InvalidArgumentException ignored) { }
	}

	@Override
	protected void onResume() {
		super.onResume();

		mSamsungPayButton.setOnClickListener(this);
		SamsungPay.isReadyToPay(mBraintreeFragment, new BraintreeResponseListener<Boolean>() {
			@Override
			public void onResponse(Boolean isReady) {
				mSamsungPayButton.setEnabled(isReady);
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();

		mSamsungPayButton.setOnClickListener(null);
	}

	@Override
	public void onClick(View v) {
		if (v == mSamsungPayButton) {
			SamsungPay.startSamsungPay(mBraintreeFragment, makeBuilder(), this);
		}
	}

	@Nullable
	@Override
	public PaymentInfo.Amount onAddressUpdated(@NotNull PaymentInfo paymentInfo) {
		return null;
	}

	@Nullable
	@Override
	public PaymentInfo.Amount onCardInfoUpdated(@NotNull CardInfo cardInfo) {
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
