package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class BillingService(
    private val paymentProvider: PaymentProvider, private val invoiceService: InvoiceService
) {

// TODO - Add code e.g. here
    fun charge() : List<String>{
    // create threadPool based on the currency Size
    val executor = Executors.newFixedThreadPool(Currency.values().size)
    Currency.values().sorted().forEach {
        //Now with currency wise thread, hit database and fetch the information for that particular currency
        val date = Calendar.getInstance().time
        val formatter = SimpleDateFormat.getDateTimeInstance() //or use getDateInstance()
        val formatedDate = formatter.format(date)
        println(formatedDate)
        val workerThread = Runnable {
            println("Executing thread fetch for $it" )
            val output = listOf(invoiceService.fetchPendingInvoices(it))
            for (output in output){
                println("$output")
            }
            //Submit this batchfor Processing
            //externalService.ProcessPayment(output)
         //Submit the Output of this for Processing and handle the failure part
        }
        executor.execute(workerThread)
    }
    executor.shutdown()
    println("Finished all threads ! Are you happy")
           return emptyList()
       }
}

