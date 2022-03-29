/*
    Implements endpoints related to invoices.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging

class InvoiceService(private val dal: AntaeusDal) {
    private val logger = KotlinLogging.logger {}

    fun fetchAll(): List<Invoice> {
        return dal.fetchInvoices()
    }

    fun fetch(id: Int): Invoice {
        return dal.fetchInvoice(id) ?: throw InvoiceNotFoundException(id)
    }

    fun fetchPendingInvoices(currency: Currency) :List<Invoice> {
        logger.info ("Fetching Pending invoices for $currency" )
        return dal.fetchInvoices(currency)
        }

    fun updateInvoice(id: Int, status: InvoiceStatus){
        logger.info ("updating invoice for the customer $id")
        return dal.updateInvoice(id, status)
    }

    fun fetchPaidInvoices(currency: Currency) :List<Invoice> {
        logger.info ("Fetching Pending invoices for $currency" )
        return dal.fetchPaidInvoices(currency)
    }
}
