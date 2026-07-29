package ch.teamorg.payments

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet

@Composable
actual fun rememberCardSetupSheet(onResult: (SetupResult) -> Unit): (String, String) -> Unit {
    val context = LocalContext.current
    val paymentSheet = rememberPaymentSheet { result ->
        onResult(when (result) {
            is PaymentSheetResult.Completed -> SetupResult.Completed
            is PaymentSheetResult.Canceled -> SetupResult.Canceled
            is PaymentSheetResult.Failed -> SetupResult.Failed
        })
    }
    return { pk, secret ->
        PaymentConfiguration.init(context, pk)
        paymentSheet.presentWithSetupIntent(secret, PaymentSheet.Configuration.Builder("Teamorg").build())
    }
}
