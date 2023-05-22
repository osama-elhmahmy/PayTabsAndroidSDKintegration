package com.example.secondapp

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.payment.paymentsdk.PaymentSdkActivity
import com.payment.paymentsdk.PaymentSdkConfigBuilder
import com.payment.paymentsdk.integrationmodels.*
import com.payment.paymentsdk.save_cards.entities.PaymentSDKSavedCardInfo
import com.payment.paymentsdk.sharedclasses.interfaces.CallbackPaymentInterface


class MainActivity : ComponentActivity(), CallbackPaymentInterface {

    var TAG_PAYTABS : String = "PayTabs"
    val profileId = "######"
    val serverKey = "##########-##########-##########"
    val clientKey = "######-######-######-######"
    val locale = PaymentSdkLanguageCode.EN
    val screenTitle = "Test SDK"
    val cartId = "123456"
    val cartDesc = "cart description"
    val currency = "EGP"
    val amount = 20.0

    val tokeniseType = PaymentSdkTokenise.MERCHANT_MANDATORY // tokenise is off
    val transType = PaymentSdkTransactionType.SALE

    val tokenFormat = PaymentSdkTokenFormat.Hex32Format()

    val billingData = PaymentSdkBillingDetails(
        "City",
        "EG",
        "email1@domain.com",
        "name name",
        "01111378007", "state",
        "address street", "1124"
    )

    val shippingData = PaymentSdkShippingDetails(
        "City",
        "EG",
        "email1@domain.com",
        "name name ",
        "01111378007", "state",
        "address street", "1124"
    )

    var selected : Int? = null
    var token : String? = null
    var transaction : String? = null
    var cardScheme : String? = null
    var cardMask : String? = null
    var isTokenized : Boolean = false

    private val tokenizationPreference by lazy { getSharedPreferences("Tokenization", Context.MODE_PRIVATE) }
    lateinit var alertDialog: AlertDialog.Builder

    private val methodsByNames = listOf(::tokenPaymentProceed, ::cardPaymentTokenizedWith3DS).associateBy { it.name }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

        val configData = PaymentSdkConfigBuilder(profileId, serverKey, clientKey, amount ?: 0.0, currency)
            .setCartDescription(cartDesc)
            .setLanguageCode(locale)
            .setMerchantIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_launcher_background, null))
            .setBillingData(billingData)
            .setMerchantCountryCode("EG") // ISO alpha 2
            .setShippingData(shippingData)
            .setCartId(cartId)
            .setTransactionType(transType)
            .showBillingInfo(false)
            .showShippingInfo(true)
            .forceShippingInfo(true)
            .setScreenTitle(screenTitle)
            .setTokenise(tokeniseType)
            .build()

        val payWithCardBtn      = findViewById<Button>(R.id.payWithCard)
        val payWithTokenBtn     = findViewById<Button>(R.id.payWithToken)
        val payWithToken3DSBtn  = findViewById<Button>(R.id.payWith3DS)
        val payWithSavedCardBtn = findViewById<Button>(R.id.payWithSavedCards)
        val clearTokenDataBtn = findViewById<Button>(R.id.clearTokenData)


        payWithCardBtn.setOnClickListener {
            cardPayment(configData)
        }

        payWithTokenBtn.setOnClickListener {
            selectCard("tokenPaymentProceed", configData)
        }

        payWithToken3DSBtn.setOnClickListener {
            selectCard("cardPaymentTokenizedWith3DS", configData)
        }

        payWithSavedCardBtn.setOnClickListener {
            payWithSavedCard(configData)
        }

        clearTokenDataBtn.setOnClickListener {
            tokenizationPreference.edit().clear().apply()
            Toast.makeText(this, "Data cleared.", Toast.LENGTH_SHORT).show()
        }

    }

    private fun payWithSavedCard(configData: PaymentSdkConfigurationDetails) {
        PaymentSdkActivity.startPaymentWithSavedCards(this,
            configData,
            true,
             this)
    }

    private fun selectCard(methodName: String, configData: PaymentSdkConfigurationDetails) {

        val allTokens = tokenizationPreference.getAll()
        val transactionsCardMask = allTokens.filter { (key, value) -> key.endsWith("_cardMask") }
        var transactionsCardSchemeList : ArrayList<String> = arrayListOf()
        var transactionsCardData = mutableMapOf<String , String>()

        for ((key, value) in transactionsCardMask) {
            transactionsCardSchemeList.add(value.toString())
            transactionsCardData[key.split("_").first()] = value.toString()
        }

        if(0 in transactionsCardSchemeList.indices){
            alertDialog = AlertDialog.Builder(this)

            alertDialog.setTitle("Select the tokenized card").setSingleChoiceItems(transactionsCardSchemeList.toTypedArray(), -1){
                    dialogInterface, i ->
                selected =  i;
                token = transactionsCardData.keys.toList().toTypedArray()[i]
                cardMask = transactionsCardSchemeList.toTypedArray()[i]
                transaction = allTokens.get(token+"_transaction").toString()
                cardScheme = allTokens.get(token+"_cardScheme").toString()

            }
            alertDialog.setPositiveButton("Proceed")
            { dialog, which ->

                if(selected != null){
                    methodsByNames[methodName]?.let { it(configData, token!!, transaction!!) }
                }
                selected = null

            }
            alertDialog.setNegativeButton("Cancel"){ dialog, which ->
                dialog.cancel()
                selected = null
            }
            alertDialog.setOnDismissListener {
                selected = null
            }
            alertDialog.show()


        }else{
            Toast.makeText(
                this,
                "You need to make one Authorized transaction at least.",
                Toast.LENGTH_LONG
            ).show()
        }

    }

    public fun tokenPaymentProceed( configData: PaymentSdkConfigurationDetails, token : String, transactionRef : String) {
        PaymentSdkActivity.startTokenizedCardPayment(this, configData, token, transactionRef, this)
    }

    fun cardPayment(configData: PaymentSdkConfigurationDetails){
        isTokenized = true;
        PaymentSdkActivity.startCardPayment(this, configData, this)

    }

    fun cardPaymentTokenizedWith3DS(configData: PaymentSdkConfigurationDetails,  token : String, transactionRef : String){

        PaymentSdkActivity.start3DSecureTokenizedCardPayment(context = this,
            ptConfigData = configData,
            savedCardInfo = PaymentSDKSavedCardInfo(cardMask!!, cardScheme!!),
            token = token!!,
            callback = this)
    }


    override fun onError(error: PaymentSdkError) {
        Log.d(TAG_PAYTABS, "onError: $error")
        Toast.makeText(this, "${error.msg}", Toast.LENGTH_SHORT).show()
    }

    override fun onPaymentFinish(PaymentSdkTransactionDetails: PaymentSdkTransactionDetails) {

        if(isTokenized && PaymentSdkTransactionDetails.isAuthorized == true) {
            val editor = tokenizationPreference.edit()

            editor.putString(
                PaymentSdkTransactionDetails.token + "_transaction",
                PaymentSdkTransactionDetails.transactionReference
            )
            editor.putString(
                PaymentSdkTransactionDetails.token + "_cardMask",
                PaymentSdkTransactionDetails.paymentInfo?.paymentDescription
            )
            editor.putString(
                PaymentSdkTransactionDetails.token + "_cardScheme",
                PaymentSdkTransactionDetails.paymentInfo?.cardScheme
            )
            editor.commit()
        }

        isTokenized = false

        Toast.makeText(
            this,
            "${PaymentSdkTransactionDetails.paymentResult?.responseMessage}",
            Toast.LENGTH_SHORT
        ).show()
        Log.d(TAG_PAYTABS, "onPaymentFinish: $PaymentSdkTransactionDetails")

    }

    override fun onPaymentCancel() {
        Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()
        Log.d(TAG_PAYTABS, "onPaymentCancel:")

    }

}


