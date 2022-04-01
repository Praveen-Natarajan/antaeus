package io.pleo.antaeus.models

import java.util.*

data class Audit(
        val id: Int,
        val fromstatus: String,
        val toStatus: String,
        val timestamp: Date

)