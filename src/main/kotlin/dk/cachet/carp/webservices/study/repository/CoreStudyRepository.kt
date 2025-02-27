package dk.cachet.carp.webservices.study.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.deployments.application.users.StudyInvitation
import dk.cachet.carp.studies.domain.Study
import dk.cachet.carp.studies.domain.StudyRepository
import dk.cachet.carp.studies.domain.StudySnapshot
import dk.cachet.carp.webservices.collection.repository.CollectionRepository
import dk.cachet.carp.webservices.common.configuration.internationalisation.service.MessageBase
import dk.cachet.carp.webservices.consent.repository.ConsentDocumentRepository
import dk.cachet.carp.webservices.dataPoint.repository.DataPointRepository
import dk.cachet.carp.webservices.document.repository.DocumentRepository
import dk.cachet.carp.webservices.export.repository.ExportRepository
import dk.cachet.carp.webservices.file.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
// See note on @CoreStudyRepository.remove(UUID)
@Suppress("LongParameterList")
class CoreStudyRepository(
    private val studyRepository: dk.cachet.carp.webservices.study.repository.StudyRepository,
    private val participantRepository: CoreParticipantRepository,
    private val dataPointRepository: DataPointRepository,
    private val collectionRepository: CollectionRepository,
    private val consentDocumentRepository: ConsentDocumentRepository,
    private val documentRepository: DocumentRepository,
    private val exportRepository: ExportRepository,
    private val filesRepository: FileRepository,
    private val objectMapper: ObjectMapper,
    private val validationMessages: MessageBase,
) : StudyRepository {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override suspend fun add(study: Study) =
        withContext(Dispatchers.IO) {
            check(studyRepository.getByStudyId(study.id.stringRepresentation) == null) {
                LOGGER.warn("Study already exists, id: ${study.id.stringRepresentation}")
                validationMessages.get("study.core.add.exists", study.id.stringRepresentation)
            }

            val studyToSave = dk.cachet.carp.webservices.study.domain.Study()

            study.invitation =
                StudyInvitation(
                    study.invitation.name,
                    study.invitation.description,
                    study.id.stringRepresentation,
                )

            studyToSave.snapshot = objectMapper.valueToTree(study.getSnapshot())
            studyRepository.save(studyToSave)

            LOGGER.info("Study saved, id: ${study.id.stringRepresentation}")
        }

    override suspend fun getById(studyId: UUID): Study? =
        withContext(Dispatchers.IO) {
            val study = studyRepository.getByStudyId(studyId.stringRepresentation)

            if (study == null) {
                LOGGER.info("Study is not found, id: ${studyId.stringRepresentation}")
                return@withContext null
            }

            convertStudySnapshotNodeToStudy(study.snapshot!!)
        }

    override suspend fun getForOwner(ownerId: UUID): List<Study> =
        withContext(Dispatchers.IO) {
            val studies = studyRepository.findAllByOwnerId(ownerId.stringRepresentation)
            studies.map { convertStudySnapshotNodeToStudy(it.snapshot!!) }.toList()
        }

    /**
     * TODO: This should only remove the study from the study repository.
     * TODO: All associated data should be deleted by subscribing to `StudyService.Event.StudyRemoved`
     */
    @Transactional(rollbackFor = [Exception::class])
    override suspend fun remove(studyId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            val idsToRemove = getDeploymentIdsOrThrow(studyId)
            val collectionIds = collectionRepository.getCollectionIdsByStudyId(studyId.stringRepresentation)

            documentRepository.deleteAllByCollectionIds(collectionIds)

            collectionRepository.deleteAllByDeploymentIds(idsToRemove.map { it.stringRepresentation })
            consentDocumentRepository.deleteAllByDeploymentIds(idsToRemove.map { it.stringRepresentation })
            dataPointRepository.deleteAllByDeploymentIds(idsToRemove.map { it.stringRepresentation })

            filesRepository.deleteByStudyId(studyId.stringRepresentation)
            exportRepository.deleteByStudyId(studyId.stringRepresentation)
            studyRepository.deleteByStudyId(studyId.stringRepresentation)

            LOGGER.info("Study with id ${studyId.stringRepresentation} and all associated data deleted.")

            true
        }

    override suspend fun update(study: Study) =
        withContext(Dispatchers.IO) {
            val existingStudy = studyRepository.getByStudyId(study.id.stringRepresentation)

            checkNotNull(existingStudy) {
                LOGGER.warn("Study is not found, id: ${study.id.stringRepresentation}")
                validationMessages.get("study.core.update.study.not_found", study.id.stringRepresentation)
            }

            existingStudy.snapshot = objectMapper.valueToTree(study.getSnapshot())
            studyRepository.save(existingStudy)

            LOGGER.info("Study updated, id: ${study.id.stringRepresentation}")
        }

    fun findAllByStudyIds(studyIds: List<UUID>): List<Study> =
        studyRepository.findAllByStudyIds(studyIds.map { it.stringRepresentation })
            .map { convertStudySnapshotNodeToStudy(it.snapshot!!) }
            .toList()

    suspend fun getWSStudyById(id: UUID): dk.cachet.carp.webservices.study.domain.Study =
        withContext(Dispatchers.IO) {
            val study = studyRepository.getByStudyId(id.stringRepresentation)

            checkNotNull(study) {
                LOGGER.warn("Study is not found, id: ${id.stringRepresentation}")
                validationMessages.get("study.core.study.not_found", id.stringRepresentation)
            }

            study
        }

    suspend fun getDeploymentIdsOrThrow(studyId: UUID): Set<UUID> {
        val recruitment = participantRepository.getRecruitment(studyId)
        return recruitment?.participantGroups?.keys ?: emptySet()
    }

    suspend fun getStudySnapshotById(id: UUID): StudySnapshot {
        val study = getWSStudyById(id)
        return objectMapper.treeToValue(study.snapshot, StudySnapshot::class.java)
    }

    fun convertStudySnapshotNodeToStudy(node: JsonNode): Study {
        val snapshot = objectMapper.treeToValue(node, StudySnapshot::class.java)
        return Study.fromSnapshot(snapshot)
    }
}
