package dk.cachet.carp.webservices.protocol.controller

import dk.cachet.carp.protocols.infrastructure.ProtocolFactoryServiceRequest
import dk.cachet.carp.protocols.infrastructure.ProtocolServiceRequest
import dk.cachet.carp.webservices.common.constants.PathVariableName
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import dk.cachet.carp.webservices.common.services.CoreServiceContainer
import dk.cachet.carp.webservices.protocol.dto.ProtocolOverview
import dk.cachet.carp.webservices.protocol.service.ProtocolService
import dk.cachet.carp.webservices.security.authentication.service.AuthenticationService
import io.swagger.v3.oas.annotations.Operation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
class ProtocolController(
    private val services: CoreServiceContainer,
    private val authenticationService: AuthenticationService,
    private val protocolService: ProtocolService,
) {
    companion object {
        val LOGGER: Logger = LogManager.getLogger()

        /** Path variables */
        const val PROTOCOL_SERVICE = "/api/protocol-service"
        const val PROTOCOL_FACTORY_SERVICE = "/api/protocol-factory-service"
        const val GET_PROTOCOL_OVERVIEW = "/api/protocols/{${PathVariableName.PROTOCOL_ID}}/latest"
        const val GET_PROTOCOLS_OVERVIEW = "/api/protocols-overview"
    }

    @PostMapping(value = [PROTOCOL_SERVICE])
    @Operation(tags = ["protocol/protocols.json"])
    suspend fun protocols(
        @RequestBody request: ProtocolServiceRequest<*>,
    ): ResponseEntity<Any> {
        LOGGER.info("Start POST: $PROTOCOL_SERVICE -> ${ request::class.simpleName }")
        return protocolService.core.invoke(request).let { ResponseEntity.ok(it) }
    }

    @PostMapping(value = [PROTOCOL_FACTORY_SERVICE])
    @Operation(tags = ["protocol/protocolFactory.json"])
    suspend fun protocolFactory(
        @RequestBody request: ProtocolFactoryServiceRequest<*>,
    ): ResponseEntity<Any> {
        LOGGER.info("Start POST: $PROTOCOL_FACTORY_SERVICE -> ${ request::class.simpleName }")
        return services.protocolFactoryService.invoke(request).let { ResponseEntity.ok(it) }
    }

    @GetMapping(value = [GET_PROTOCOL_OVERVIEW])
    @PreAuthorize("hasRole('RESEARCHER')")
    @Operation(tags = ["protocol/getLatestProtocolById.json"])
    suspend fun getSingleProtocolOverview(
        @PathVariable(PathVariableName.PROTOCOL_ID) protocolId: String,
    ): ProtocolOverview {
        LOGGER.info("/api/protocols/$protocolId/latest")
        return protocolService.getSingleProtocolOverview(protocolId)
            ?: throw ResourceNotFoundException("No protocol found with id $protocolId.")
    }

    @GetMapping(value = [GET_PROTOCOLS_OVERVIEW])
    @PreAuthorize("hasRole('RESEARCHER')")
    suspend fun getProtocolsOverview(): List<ProtocolOverview> {
        LOGGER.info("Start GET: /api/protocols")
        return protocolService.getProtocolsOverview(authenticationService.getId())
    }
}
