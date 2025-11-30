package traversium.socialservice.event

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import traversium.commonmultitenancy.TenantContext
import traversium.notification.kafka.NotificationStreamData
import traversium.socialservice.kafka.KafkaProperties

@Component
@ConditionalOnProperty(name = ["spring.kafka.notification-topic"])
class NotificationEventListener(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val kafkaProperties: KafkaProperties
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun sendNotificationDataToKafka(notification: NotificationStreamData) {
        val tenantId = TenantContext.getTenant()

        val record = ProducerRecord<String, Any>(kafkaProperties.notificationTopic!!, notification)
        tenantId.let {
            record.headers().add(RecordHeader("tenantId", it.toByteArray()))
        }

        kafkaTemplate.send(record)
    }
}

