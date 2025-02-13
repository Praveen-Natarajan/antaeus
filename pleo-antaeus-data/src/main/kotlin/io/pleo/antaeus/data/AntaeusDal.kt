/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.time.LocalDateTime

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id)
    }

    fun fetchInvoices(currency: Currency): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                    .select { (InvoiceTable.currency.eq(currency.name)) and (InvoiceTable.status.eq(InvoiceStatus.PENDING.name)) }
                    .map { it.toInvoice() }
        }
    }

    fun updateInvoice(id: Int, status: InvoiceStatus) {
        transaction(db){
            InvoiceTable.update({InvoiceTable.id eq id }){
                it[this.status] = status.name
            }
        }
    }

    fun fetchPaidInvoices(currency: Currency): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                    .select { (InvoiceTable.currency.eq(currency.name)) and (InvoiceTable.status.eq(InvoiceStatus.PAID.name)) }
                    .map { it.toInvoice() }
        }
    }

    fun fetchFailedInvoices(currency: Currency): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                    .select { (InvoiceTable.currency.eq(currency.name)) and (InvoiceTable.status.eq(InvoiceStatus.FAILED.name)) }
                    .map { it.toInvoice() }
        }
    }

    fun updateAuditTable(id:Int, fromStatus: InvoiceStatus, toStatus: InvoiceStatus){
        return transaction (db) {
            AuditTable.insert {
                it[this.id] = id
                it[this.fromstatus] = fromStatus.toString()
                it[this.tostatus] = toStatus.toString()
                it[this.timestamp] = DateTime.now()
            }
        }
    }

    fun fetchAuditInfo(): List<Audit> {
        return transaction(db) {
            AuditTable
                    .selectAll()
                    .map { it.toAudit() }
        }
    }

}
