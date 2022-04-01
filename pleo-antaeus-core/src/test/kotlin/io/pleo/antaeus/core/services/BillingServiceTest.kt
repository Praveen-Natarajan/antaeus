package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BillingServiceTest {
    private val paymentProvider = mockk<PaymentProvider>()
    private val invoiceService = mockk<InvoiceService>()
    private val customerService = mockk<CustomerService>()
    private val Kafkaservice = mockk<KafkaService>()

    private val invoice = Invoice(23, 23, Money(BigDecimal.valueOf(1000), Currency.EUR), InvoiceStatus.PENDING)

    private val customer = Customer(
            id = 23,
            currency = Currency.EUR
    )

    // - using Spyk to mock a private method
    private val billingService = spyk(BillingService(paymentProvider = paymentProvider, invoiceService = invoiceService,
            customerService = customerService, kafkaService = Kafkaservice))

    private var dal: AntaeusDal = mockk()



    @Test
    fun `create message text from Invoice`(){
        val method = billingService.javaClass.getDeclaredMethod("createMsgTxtFromInvoice", Invoice::class.java)
        method.isAccessible = true
        val parameters = arrayOfNulls<Any>(1)
        parameters[0] = invoice
        assertEquals("23|23|PENDING|1000|EUR", method.invoke(billingService, *parameters) )
    }

    @Test
    fun `Try to reprocess the failed Payments`(){
        val method = billingService.javaClass.getDeclaredMethod("processFailedRetry", Int::class.java)
        method.isAccessible = true
        val parameters = arrayOfNulls<Any>(1)
        parameters[0] = 23
        assertEquals("Email sent to Pleo Support & Consumer" , method.invoke(billingService, *parameters) )
    }

    @Test
    fun `Try to charge customer`() {
        val method = billingService.javaClass.getDeclaredMethod("chargeInvoice", Int::class.java, InvoiceStatus::class.java)
        method.isAccessible = true
        val parameters = arrayOfNulls<Any>(2)
        parameters[0] = 23
        parameters[1] = InvoiceStatus.PENDING
        every { invoiceService.fetch(any()) } returns invoice
        every { customerService.fetch(any()) } returns customer
        verify(exactly = 0) { paymentProvider.charge(any()) }
        assertEquals(false, method.invoke(billingService, *parameters))
    }

    @Test
    fun `Try to charge set of customers`(){
        every { dal.fetchInvoices() } returns listOf(
                Invoice(id = 23, customerId = 1, amount = Money(BigDecimal.valueOf(10), Currency.EUR), status = InvoiceStatus.PENDING)
        )
        every { dal.updateInvoice(range(1, 3), any()) } returns Unit
        every { invoiceService.fetch(any()) } returns invoice
        every { paymentProvider.charge(any()) } returns true
        every { customerService.fetch(any()) } returns customer
        assertEquals( billingService.chargeInvoice(23, InvoiceStatus.PENDING),true)
    }

    @Test
    fun `Process Invoices send Message to Queue`(){
        every { invoiceService.fetchPendingInvoices(any()) } returns listOf(

        Invoice(id = 23, customerId = 1, amount = Money(BigDecimal.valueOf(10), Currency.EUR), status = InvoiceStatus.PENDING),
        Invoice(id = 24, customerId = 2, amount = Money(BigDecimal.valueOf(100), Currency.GBP), status = InvoiceStatus.PENDING),
        Invoice(id = 25, customerId = 3, amount = Money(BigDecimal.valueOf(1000), Currency.SEK), status = InvoiceStatus.PENDING)
        )
        every { Kafkaservice.sendMessage(any(),any(),any()) } returns Unit
        billingService.processInvoices()
        verify (exactly = 15) {Kafkaservice.sendMessage(any(),any(),any())}
    }

    @Test
    fun `Update status in database `(){
        val method = billingService.javaClass.getDeclaredMethod("updateStatus", Int::class.java, InvoiceStatus::class.java)
        method.isAccessible = true
        val parameters = arrayOfNulls<Any>(2)
        parameters[0] = 23
        parameters[1] = InvoiceStatus.PENDING
        every {invoiceService.updateInvoice(any(),any())} returns Unit
        assertEquals(null , method.invoke(billingService, *parameters) )
    }





}