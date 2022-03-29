package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.Executors

class BillingService(
    private val paymentProvider: PaymentProvider, private val invoiceService: InvoiceService,private val kafkaService: KafkaService,
    private val customerService: CustomerService
) {
    private val logger = KotlinLogging.logger {}

    fun processInvoices() {
    // create threadPool based on the currency Size
    val executor = Executors.newFixedThreadPool(Currency.values().size)
    Currency.values().sorted().forEach {
        //Now with currency wise thread, hit database and fetch the information for that particular currency
        val workerThread = Runnable {
            logger.info ("created thread for processing $it invoices" )
            val output : List<Invoice> = invoiceService.fetchPendingInvoices(it)
            output.forEach {
                try {
                    logger.info ("processing message $it " )
                    kafkaService.sendMessage(createMsgTxtFromInvoice(it),it.amount.currency.name)
                } catch(e : Exception) {
                    logger.error { "Error sending messages to the topic for $it" }
                }
            }
        }
        executor.execute(workerThread)
    }
    executor.shutdown()
        logger.info { "All Invoices send for Processing" }
    }

    fun consume(){
        chargeCard();
    }

    private fun createMsgTxtFromInvoice(output: Invoice) :String {
        var msg = output.id.toString()+"|"+output.customerId+"|"+output.status+"|"+output.amount.value+"|"+output.amount.currency;
        logger.info { "Message created for topic : $msg" }
        return msg;
    }

    fun chargeCard() {
        val consumer = kafkaService.createConsumer("localhost:9092")
        consumer.apply {
            subscribe(listOf("my-pleo-topic"))
        }
        consumer.use {
            while (true) {
                val records = consumer.poll(Duration.ofMillis(1000))
                records.forEach {
                    println("$it")
                    var msg = it.value().toString().split("|")
                    //-Message will be in this format -> 761|77|PENDING|356.54|SEK
                    println("$msg")
                    println(msg[0]);
                    when (chargeInvoice(msg[0].toInt())) {
                        true -> updateStatus(msg[0].toInt(), InvoiceStatus.PAID)
                        else -> processRetry(invoiceService.fetch(msg[0].toInt()), InvoiceStatus.FAILED)

                    }
                }
            }
        }
    }

    private fun processRetry(fetch: Invoice, status: InvoiceStatus) {
        updateStatus(fetch.id, InvoiceStatus.FAILED)
        //Send Msg to new Topic to process Failure status, which can be invoked Separately
    }

    private fun updateStatus(id: Int, status: InvoiceStatus) {
        invoiceService.updateInvoice(id, status)
    }

    fun chargeInvoice(id: Int): Boolean {
        var status = false
            try {
                val invoice = invoiceService.fetch(id) //Fetch and check to avoid double charge
                val customer = customerService.fetch(invoice.customerId)
                if (invoice.status == InvoiceStatus.PENDING && customer.currency == invoice.amount.currency) {
                    status = paymentProvider.charge(invoice)
                }
            } catch (e: Exception) {
                logger.error(e) { "Payment failed for invoice ${id}" }
            }

        logger.info { "Payment status of invoice ${id} is: ${status}" }
        return status
    }

    fun test(){
        Currency.values().sorted().forEach {
            val invoice = invoiceService.fetchPaidInvoices(it)
            for(invoice in invoice)
            logger.info { "paid invoices are $invoice" }
        }
    }

}

