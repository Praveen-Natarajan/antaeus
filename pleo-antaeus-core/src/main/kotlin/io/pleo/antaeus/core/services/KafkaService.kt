package io.pleo.antaeus.core.services

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.*
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.*

class KafkaService(broker: String) {

    private val producer = createProducer("localhost:9092")
    private val consumer = createConsumer("localhost:9092")


     private fun createProducer(brokers: String): Producer<String, String> {
        val props = Properties()
        props["bootstrap.servers"] = brokers
        props[KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        props[VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        return KafkaProducer<String, String>(props)
    }

     fun createConsumer(brokers: String): Consumer<String, String> {
        val props = Properties()
        props["bootstrap.servers"] = brokers
        props[KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props["group.id"] = "pleo-consumer"
        return KafkaConsumer<String, String>(props)
    }

     fun sendMessage(msg : String, currency: String){
          producer.send(ProducerRecord("my-pleo-topic", currency, msg))
     }



}