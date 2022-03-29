package io.pleo.antaeus.core.services

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class SchedulerService() {

    private val invoiceScheduler : ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    Runnable process = () -> {

    }
}