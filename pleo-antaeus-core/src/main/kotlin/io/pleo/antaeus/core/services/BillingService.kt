package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.MY_INVOICE_TOPIC
import io.pleo.antaeus.core.MY_RETRY_TOPIC
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Audit
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock

class BillingService(
    private val paymentProvider: PaymentProvider, private val invoiceService: InvoiceService,private val kafkaService: KafkaService,
    private val customerService: CustomerService
) {
    private val logger = KotlinLogging.logger {}
    private var readWriteLock:  ReentrantReadWriteLock = ReentrantReadWriteLock()


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

    private fun createMsgTxtFromInvoice(output: Invoice): String {
        readWriteLock.writeLock().lock()
        try {
            val msg = output.id.toString() + "|" + output.customerId + "|" + output.status + "|" + output.amount.value + "|" + output.amount.currency
            logger.info { "Message created for topic : $msg" }
            return msg
        } finally {
            readWriteLock.writeLock().unlock()
        }
    }


    //-Incoming Message will be in this format -> 761|77|PENDING|356.54|SEK
    fun processPendingInvoice() {
        logger.info { "--process Invoice Mechanism kicked in, processing invoice messages" }
        kafkaService.consumeInvoiceMessage()?.forEach {
            logger.info("$it")
            val msg = it.value().toString().split("|")
            logger.info("Message received : $msg")
            try {
                when (chargeInvoice(msg[0].toInt(), InvoiceStatus.PENDING)) {
                    true -> updateStatus(msg[0].toInt(), InvoiceStatus.PAID)
                    else -> processRetry(invoiceService.fetch(msg[0].toInt()))
                }
            } catch (e: CurrencyMismatchException) {
                logger.error { "processing message failed with CurrencyMismatchException::$msg[0] " }
                //- Call notification service for Pleo & customer
                //we aren't going to retry the currency mismatch one's as it will fail again
            } catch (e: Exception) {
                logger.error { "processing message failed::$msg[0]" }
            }
        }
    }

    private fun processRetry(invoice: Invoice) {
        updateStatus(invoice.id, InvoiceStatus.FAILED)
        logger.info { "sending message for retry $invoice to topic: $MY_RETRY_TOPIC & status : ${InvoiceStatus.FAILED}" }
        //Send Msg to new Topic to process Failure status, which will be invoked Separately
        kafkaService.sendMessage(createMsgTxtFromInvoice(invoice),invoice.amount.currency.name, MY_RETRY_TOPIC)
    }

    private fun updateStatus(id: Int, status: InvoiceStatus) {
        readWriteLock.writeLock().lock()
        try {
            invoiceService.updateInvoice(id, status)
        }catch (e: Exception){
            logger.error { "Error updating invoice status in the database" }
        }finally {
            readWriteLock.writeLock().unlock()
        }
    }

    fun chargeInvoice(id: Int, invoiceStatus: InvoiceStatus): Boolean {
        readWriteLock.writeLock().lock()
        var status = false
        try {
            try {
                val invoice = invoiceService.fetch(id) //Fetch and check to avoid double charge
                val customer = customerService.fetch(invoice.customerId)
                if (invoice.status == invoiceStatus && customer.currency == invoice.amount.currency) {
                    status = paymentProvider.charge(invoice)
                } else {
                    if (customer.currency != invoice.amount.currency) {
                        // - Fail faster and fail safe, instead of failing at external vendor level
                        logger.error { "Currency Mismatch with the invoice::$invoice.id for Customer ${customer.id}" }
                        throw CurrencyMismatchException(invoice.id, customer.id)
                    } else if (invoice.status != invoiceStatus) {
                        logger.error { "Invoice status from the message and database are different::$invoice.id for Customer ${customer.id}" }
                    }
                }

                // - Update Audit table for Auditing Purposes
                createAuditEntry(id, invoiceStatus, status)

            } catch (e: InvoiceNotFoundException) {
                logger.error(e) { "Invoice not found in the database InvoiceId:: $id" }
                return false
            } catch (e: NetworkException) {
                logger.error(e) { "unable to charge due to network failure for Invoice:: $id" }
                return false
            } catch (e: Exception) {
                logger.error(e) { "Payment failed for invoice $id" }
                return false
            }

            logger.info { "Payment status of invoice $id is: $status" }
            return status
        }finally {
            readWriteLock.writeLock().unlock()
        }
    }

    private fun createAuditEntry(id: Int, invoiceStatus: InvoiceStatus, status: Boolean) {
        readWriteLock.writeLock().lock()
        try {
            if (status) {
                invoiceService.createAudit(id, invoiceStatus, InvoiceStatus.PAID)
            } else {
                if (invoiceStatus == InvoiceStatus.PENDING) {
                    invoiceService.createAudit(id, invoiceStatus, InvoiceStatus.FAILED)
                } else {
                    invoiceService.createAudit(id, invoiceStatus, InvoiceStatus.RETRY_FAILED)
                }
            }
        } catch (e: Exception) {
            logger.error { "Error updating invoice status in Audit table" }
        } finally {
            readWriteLock.writeLock().unlock()
        }

    }

    fun getPaidInvoices(): MutableList<Any> {
        val mutableListAny: MutableList<Any> = mutableListOf()
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
        val mutableListAny: MutableList<Any> = mutableListOf()
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
            val msg = it.value().toString().split("|")
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

