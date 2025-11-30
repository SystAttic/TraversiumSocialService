package traversium.socialservice.event

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import traversium.audit.kafka.AuditStreamData
import traversium.socialservice.kafka.KafkaProperties
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty(name = ["spring.kafka.audit-topic"])
class AuditEventListener(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val kafkaProperties: KafkaProperties
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun sendAuditDataToKafka(event: AuditStreamData) {
        kafkaTemplate.send(
            kafkaProperties.auditTopic!!,
            event,
        )[kafkaProperties.clientConfirmationTimeout, TimeUnit.SECONDS]
    }
}

