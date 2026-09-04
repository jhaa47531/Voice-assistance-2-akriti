package com.example.data.api.provider

import java.io.IOException

class ProviderException(
    val statusCode: Int,
    override val message: String,
    cause: Throwable? = null
) : IOException(message, cause)
