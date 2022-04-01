package io.pleo.antaeus.core.services

import mu.KotlinLogging
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class SchedulerService(private val billingService: BillingService) {

    private val logger = KotlinLogging.logger {}

    fun scheduleTasks() {
        processInvoice()  // process pending records from database and send to Topic
        processPendingInvoice()   //  start Kafka consumer and do the actual charging part
        processretry()  // Start the retry consumer and do a retry
    }

    //Advantage of using executor service is that it will handle the next run, even if the previous run failed
    private fun processInvoice() {
        val executorService = Executors.newSingleThreadScheduledExecutor()
        try {
            executorService.scheduleAtFixedRate({
                billingService.processInvoices()
            }, 0, getMilliseconds(), TimeUnit.MILLISECONDS)
        } catch (e: java.lang.Exception) {
            logger.error { "process Invoices Task Failed ${e.localizedMessage}" }
        }
    }

    private fun processretry() {
        try {
            val executorService = Executors.newSingleThreadScheduledExecutor()
            executorService.scheduleWithFixedDelay({
                billingService.retryInvoices()
            }, 0, 1, TimeUnit.MINUTES)
            logger.info { "---Retry mechanism kicked in ---" }
        } catch (e: java.lang.Exception) {
            logger.error { "process Failed invoices Task Failed ${e.localizedMessage}" }
        }
    }

    private fun processPendingInvoice() {
        try {
            val executorService = Executors.newSingleThreadScheduledExecutor()
            executorService.scheduleWithFixedDelay({
                billingService.processPendingInvoice()
            }, 0, 30, TimeUnit.SECONDS)
            logger.info { "--- process pending Invoice kicked in ---" }
        } catch (e: java.lang.Exception) {
            logger.error { "process Failed invoices Task Failed ${e.localizedMessage}" }
        }
    }

    private fun getMilliseconds(): Long {
        var dayOfMonth = Calendar.getInstance()

        if (dayOfMonth.get(Calendar.DAY_OF_MONTH) != 1) {
            dayOfMonth.add(Calendar.MONTH, 1)
            dayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        }
        return (dayOfMonth.timeInMillis - Calendar.getInstance().timeInMillis)
    }
}
