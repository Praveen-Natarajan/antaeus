package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Currency
import mu.KotlinLogging
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class BillingService(
    private val paymentProvider: PaymentProvider, private val invoiceService: InvoiceService
) {
    private val logger = KotlinLogging.logger {}

    fun charge() {
    // create threadPool based on the currency Size
    val executor = Executors.newFixedThreadPool(Currency.values().size)
    Currency.values().sorted().forEach {
        //Now with currency wise thread, hit database and fetch the information for that particular currency
        val workerThread = Runnable {
            logger.info ("created thread for processing $it invoices" )
            val output = listOf(invoiceService.fetchPendingInvoices(it))
            for (output in output){
                logger.info {"$output"}
            }
         // Send these messages to Kafka Topic
        //externalService.ProcessPayment(output)
         //Submit the Output of this for Processing and handle the failure part
        }
        executor.execute(workerThread)
    }
    executor.shutdown()
    println("Finished all threads ! Are you happy")
       }
}

