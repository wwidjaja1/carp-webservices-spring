package dk.cachet.carp.webservices.deployment.serdes

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import dk.cachet.carp.deployments.application.users.ParticipantData
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.common.exception.serialization.SerializationException
import dk.cachet.carp.webservices.common.input.WS_JSON
import kotlinx.serialization.encodeToString
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

/**
 * The Class [ParticipantDataSerializer].
 * The [ParticipantDataSerializer] implements the serialization logic for [ParticipantData].
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
class ParticipantDataSerializer(private val validationMessages: MessageBase) : JsonSerializer<ParticipantData>() {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    /**
     * The [serialize] function is used to serialize the parsed object.
     *
     * @param value The [ParticipantData] object containing the json object parsed.
     * @param gen The [JsonGenerator] to write JSON content generated from the study deployment snapshot.
     * @param serializers The [SerializerProvider] to serialize the parsed object from the study deployment snapshot.
     * @throws SerializationException If the [ParticipantData] is blank or empty.
     * Also, if the [ParticipantData] contains invalid format.
     * @return The serialization of deployment service request object.
     */
    override fun serialize(
        value: ParticipantData?,
        gen: JsonGenerator?,
        serializers: SerializerProvider?,
    ) {
        if (value == null) {
            LOGGER.error("The core ParticipantData is null.")
            throw SerializationException(validationMessages.get("deployment.participant_data.serialization.empty"))
        }

        val serialized: String
        try {
            serialized = WS_JSON.encodeToString(value)
        } catch (ex: Exception) {
            LOGGER.error("The core ParticipantData is not valid. Exception: ${ex.message}")
            throw SerializationException(
                validationMessages.get("deployment.participant_data.serialization.error", ex.message.toString()),
            )
        }

        gen!!.writeRawValue(serialized)
    }
}
