package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.MY_PLEO_TOPIC
import io.pleo.antaeus.core.MY_RETRY_TOPIC
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
                    kafkaService.sendMessage(createMsgTxtFromInvoice(it),it.amount.currency.name, MY_PLEO_TOPIC)
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

     private fun createMsgTxtFromInvoice(output: Invoice) :String {
        var msg = output.id.toString()+"|"+output.customerId+"|"+output.status+"|"+output.amount.value+"|"+output.amount.currency;
        logger.info { "Message created for topic : $msg" }
        return msg;
    }

    fun processPendingInvoice() {
        val consumer = kafkaService.createConsumer("localhost:9092")
        consumer.apply {
            subscribe(listOf(MY_PLEO_TOPIC))
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
                    when (chargeInvoice(msg[0].toInt(), InvoiceStatus.PENDING)) {
                        true -> updateStatus(msg[0].toInt(), InvoiceStatus.PAID)
                        else -> processRetry(invoiceService.fetch(msg[0].toInt()), InvoiceStatus.FAILED)

                    }
                }
            }
        }
    }

    private fun processRetry(invoice: Invoice, status: InvoiceStatus) {
        updateStatus(invoice.id, InvoiceStatus.FAILED)
        logger.info { "sending message for retry $invoice to topic: $MY_RETRY_TOPIC" }
        //Send Msg to new Topic to process Failure status, which will be invoked Separately
        kafkaService.sendMessage(createMsgTxtFromInvoice(invoice),invoice.amount.currency.name, MY_RETRY_TOPIC)
    }

    private fun updateStatus(id: Int, status: InvoiceStatus) {
        invoiceService.updateInvoice(id, status)
    }

    fun chargeInvoice(id: Int, invoiceStatus: InvoiceStatus): Boolean {
        var status = false
            try {
                val invoice = invoiceService.fetch(id) //Fetch and check to avoid double charge
                val customer = customerService.fetch(invoice.customerId)
                if (invoice.status == invoiceStatus && customer.currency == invoice.amount.currency) {
                    status = paymentProvider.charge(invoice)
                }
            } catch (e: Exception) {
                logger.error(e) { "Payment failed for invoice ${id}" }
            }

        logger.info { "Payment status of invoice ${id} is: ${status}" }
        return status
    }

    fun getPaidInvoices(){
        Currency.values().sorted().forEach {
            val invoice = invoiceService.fetchPaidInvoices(it)
            for(invoice in invoice)
            logger.info { "paid invoices are $invoice" }
        }
    }

    fun getFailedInvoices(){
        Currency.values().sorted().forEach {
            val output : List<Invoice> = invoiceService.fetchFailedInvoices(it)
            output.forEach {
                logger.info { "Failed invoices are $it" }
            }
            logger.info { "Method completed" }
        }
    }

    fun retryInvoices(){
        logger.info { "--Retry Mechanism kicked in, processing retry messages" }
        val consumer = kafkaService.createConsumer("localhost:9092")
        consumer.apply {
            subscribe(listOf(MY_RETRY_TOPIC))
        }
        consumer.use {
            while (true) {
                val records = consumer.poll(Duration.ofMillis(1000))
                records.forEach {
                    logger.info { "Retrying payment for invoice: $it" }
                    var msg = it.value().toString().split("|")
                    //-Message will be in this format -> 761|77|PENDING|356.54|SEK
                    when (chargeInvoice(msg[0].toInt(), InvoiceStatus.FAILED)) {
                        true -> updateStatus(msg[0].toInt(), InvoiceStatus.PAID)
                        else -> processFailedRetry(msg[0].toInt())

                    }
                }
            }
        }
    }

    private fun processFailedRetry(id:Int) : String {
         //#TODO - send message and notify both Pleo team and the card Owner
         // Implementation to notify the customer and the corresponding Pleo,operation executive about the Failure
         // Decision needs to be taken on sending a consolidated email vs mail for each failure
        logger.info { "Msg sent to Pleo support & consumer for id : $id" }
        return "Email sent to Pleo Support & Consumer"
    }
}

