package com.braintreepayments.demo.samsungpay

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View

class LaunchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)
    }

    fun launchJavaDemo(@Suppress("UNUSED_PARAMETER") v: View) {
        val intent = Intent(this, MainActivity::class.java)
            .putExtras(Bundle())

        startActivity(intent)
    }

    fun launchKotlinDemo(@Suppress("UNUSED_PARAMETER") v: View) {
        val intent = Intent(this, MainKotlinActivity::class.java)
            .putExtras(Bundle())

        startActivity(intent)
    }
}