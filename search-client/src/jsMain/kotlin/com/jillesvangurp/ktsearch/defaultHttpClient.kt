package com.jillesvangurp.ktsearch

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.headers

actual fun defaultKtorHttpClient(
    logging: Boolean,
    user: String?,
    password: String?,
    elasticApiKey: String?,
): HttpClient {
    return HttpClient(Js) {
        if (logging) {
            install(Logging) {
                level = LogLevel.ALL
            }
        } else {
            install(Logging) {
                level = LogLevel.NONE
            }
        }
        if (!user.isNullOrBlank() && !password.isNullOrBlank()) {
            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(user,password)
                    }
                    sendWithoutRequest {
                        true
                    }
                }
            }
        }
        if(!elasticApiKey.isNullOrBlank()) {
            headers {
                append("Authorization", "ApiKey $elasticApiKey")
            }
        }
    }
}