package org.example

import com.google.gson.Gson
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import no.entur.abt.core.exchange.grpc.CustomerAccountServiceGrpc
import no.entur.abt.core.exchange.pb.v1.GetCustomerAccountCommand
import no.ruter.sb.idb.common.grpc.DeadlineInterceptor
import no.ruter.sb.idb.common.grpc.RequestResponseLoggingInterceptor
import no.ruter.sb.idb.common.grpc.RequestResponseLoggingInterceptorConfig
import no.ruter.sb.idb.common.types.CommandId
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

fun main() {
    val channel = grpcChannel()
    val customerAccountService = customerAccountService(channel)

    val command = GetCustomerAccountCommand.newBuilder()
        .setCommandId(CommandId.random())
        .setCustomerAccountId("RUT:CustomerAccount:${UUID.randomUUID()}")
        .build()

    val exceptions = mutableListOf<Class<Any>>()

    repeat(10) {
        val start = System.currentTimeMillis()
        runCatching {
            CompletableFuture.supplyAsync {
                customerAccountService.getCustomerAccount(command)
            }.get(15, TimeUnit.SECONDS) // force call to exit after 15s
        }.getOrElse {
            logger.info("Exception after ${System.currentTimeMillis() - start}ms: ${it.javaClass} - ${it.message}")
            exceptions.add(it.javaClass)
            null
        }
    }

    exceptions
        .groupBy { it }
        .map { it.value.size to it.value.first() }
        .also { println("Exceptions: $it") }

}

private fun customerAccountService(
    channel: ManagedChannel,
): CustomerAccountServiceGrpc.CustomerAccountServiceBlockingStub =
    CustomerAccountServiceGrpc.newBlockingStub(channel)
        .withInterceptors(
            RequestResponseLoggingInterceptor(
                config = RequestResponseLoggingInterceptorConfig(
                    logPayload = true
                )
            ),
            DeadlineInterceptor(5, TimeUnit.SECONDS)
        )


fun grpcChannel(): ManagedChannel {
    println(CustomerAccountServiceGrpc.getServiceDescriptor().name)

    return ManagedChannelBuilder
        .forAddress("localhost", 1337) // simulate UNAVAILABLE
        .usePlaintext()
        .enableRetry()
        .defaultServiceConfig(
            /*
            https://github.com/grpc/proposal/blob/master/A6-client-retries.md

            initial attempt: random(0, initialBackoff) // 0-2sek
            next 4 attempts: random(0, min(initialBackoff*backoffMultiplier**(n-1), maxBackoff)) //0-2sek

            should have about ~50% probability of exceeding the deadline (5s)
             */
            getRetryingServiceConfig(
                """
                    {
                        "methodConfig": [
                            {
                                "name": [
                                    { "service": "no.entur.abt.core.v1.CustomerAccountService" }
                                ],
                                "retryPolicy": {
                                    "maxAttempts" : 6,
                                    "initialBackoff" : "2.0s",
                                    "maxBackoff": "2.0s",
                                    "backoffMultiplier": 10000,
                                    "retryableStatusCodes": [ "UNAVAILABLE" ]
                                }
                            }
                        ]
                    }
                """.trimIndent()
            )
        )
        .build()
}

fun getRetryingServiceConfig(json: String): Map<String, *> {
    return Gson().fromJson<Map<String, *>>(json, MutableMap::class.java)
}



