package ch.teamorg.payments

fun setupIntentIdFrom(clientSecret: String): String = clientSecret.substringBefore("_secret_")
