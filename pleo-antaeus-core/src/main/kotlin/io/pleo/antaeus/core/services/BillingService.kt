package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.MY_INVOICE_TOPIC
import io.pleo.antaeus.core.MY_RETRY_TOPIC
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Audit
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
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
                    kafkaService.sendMessage(createMsgTxtFromInvoice(it),it.amount.currency.name, MY_INVOICE_TOPIC)
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
        logger.info { "--process Invoice Mechanism kicked in, processing invoice messages" }
        kafkaService.consumeInvoiceMessage()?.forEach {
            logger.info("$it")
            var msg = it.value().toString().split("|")
            //-Message will be in this format -> 761|77|PENDING|356.54|SEK
            logger.info("Message received : $msg")
            when (chargeInvoice(msg[0].toInt(), InvoiceStatus.PENDING)) {
                true -> updateStatus(msg[0].toInt(), InvoiceStatus.PAID)
                else -> processRetry(invoiceService.fetch(msg[0].toInt()), InvoiceStatus.FAILED)

            }
        }
    }

    private fun processRetry(invoice: Invoice, status: InvoiceStatus) {
        updateStatus(invoice.id, InvoiceStatus.FAILED)
        logger.info { "sending message for retry $invoice to topic: $MY_RETRY_TOPIC & status : $status" }
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
                if (status) {
                    invoiceService.createAudit(id, invoiceStatus, InvoiceStatus.PAID)
                } else {
                    invoiceService.createAudit(id, invoiceStatus, InvoiceStatus.FAILED)
                }
            } catch (e: Exception) {
                logger.error(e) { "Payment failed for invoice ${id}" }
            }

        logger.info { "Payment status of invoice ${id} is: ${status}" }
        return status
    }

    fun getPaidInvoices(): MutableList<Any> {
        var mutableListAny: MutableList<Any> = mutableListOf<Any>()
        Currency.values().sorted().forEach {
            val output: List<Invoice> = invoiceService.fetchPaidInvoices(it)
            output.forEach {
                mutableListAny.add(it)
                logger.info { "paid invoices are $it" }
            }
        }
        return mutableListAny
    }

    fun getFailedInvoices(): MutableList<Any> {
        var mutableListAny: MutableList<Any> = mutableListOf<Any>()
        Currency.values().sorted().forEach {
            val output : List<Invoice> = invoiceService.fetchFailedInvoices(it)
            output.forEach {
                mutableListAny.add(it)
                logger.info { "Failed invoices are $it" }
            }
            logger.info { "Method completed" }
        }
        return mutableListAny
    }

    fun retryInvoices(){
        logger.info { "--Retry Mechanism kicked in, processing retry messages--" }
        kafkaService.consumeRetryMessage()?.forEach{
            logger.info("$it")
            var msg = it.value().toString().split("|")
            //-Message will be in this format -> 761|77|PENDING|356.54|SEK
            logger.info("Message received : $msg")
            when (chargeInvoice(msg[0].toInt(), InvoiceStatus.FAILED)) {
                true -> updateStatus(msg[0].toInt(), InvoiceStatus.PAID)
                else -> processFailedRetry(msg[0].toInt())

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


     fun getAuditInfo(): List<Audit> {
        return invoiceService.fetchAuditTable()
    }

}

