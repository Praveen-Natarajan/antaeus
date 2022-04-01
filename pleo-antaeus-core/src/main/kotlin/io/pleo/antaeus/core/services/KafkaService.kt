package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.*
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.*
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.*

class KafkaService(broker: String) {

    private val producer = createProducer(broker)
    private val invoiceConsumer = createConsumer(broker)
    private val retryConsumer = createConsumer(broker)


    init {
        subscribeToTopics()
    }


    private fun createProducer(broker: String): Producer<String, String> {
        val props = Properties()
        props[BOOTSTRAP_SERVERS] = broker
        props[KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        props[VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        return KafkaProducer<String, String>(props)
    }

     fun createConsumer(brokers: String): Consumer<String, String> {
        val props = Properties()
        props[BOOTSTRAP_SERVERS] = brokers
        props[KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[GROUP_ID] = PLEO_CONSUMER
        return KafkaConsumer<String, String>(props)
    }

     fun sendMessage(msg : String, currency: String, topic:String){
          producer.send(ProducerRecord(topic, currency, msg))
     }


    private fun subscribeToTopics() {
        invoiceConsumer.subscribe(listOf(MY_INVOICE_TOPIC))
        retryConsumer.subscribe(listOf(MY_RETRY_TOPIC))
    }

    fun consumeInvoiceMessage() : ConsumerRecords<String, String>?{
        return invoiceConsumer.poll(Duration.ofMillis(1000))
    }

    fun consumeRetryMessage() : ConsumerRecords<String, String>?{
        return retryConsumer.poll(Duration.ofMillis(1000))
    }


}