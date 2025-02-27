package dk.cachet.carp.webservices.study.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dk.cachet.carp.common.application.UUID
import dk.cachet.carp.studies.domain.users.ParticipantRepository
import dk.cachet.carp.studies.domain.users.Recruitment
import dk.cachet.carp.studies.domain.users.RecruitmentSnapshot
import dk.cachet.carp.webservices.common.exception.responses.ResourceNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CoreParticipantRepository(
    private val objectMapper: ObjectMapper,
    private val recruitmentRepository: RecruitmentRepository,
) : ParticipantRepository {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger()
    }

    override suspend fun addRecruitment(recruitment: Recruitment) =
        withContext(Dispatchers.IO) {
            val studyId = recruitment.studyId.stringRepresentation
            val existingRecruitment = recruitmentRepository.findRecruitmentByStudyId(studyId)

            check(existingRecruitment == null) { "A recruitment already exists for the study with id $studyId." }

            val snapshotJsonNode = objectMapper.valueToTree<JsonNode>(recruitment.getSnapshot())
            val newRecruitment =
                dk.cachet.carp.webservices.study.domain.Recruitment().apply {
                    snapshot = snapshotJsonNode
                }
            val saved = recruitmentRepository.save(newRecruitment)
            LOGGER.info("New recruitment with id ${saved.id} is saved for study with id $studyId.")
        }

    override suspend fun getRecruitment(studyId: UUID): Recruitment? =
        withContext(Dispatchers.IO) {
            val existingRecruitment = recruitmentRepository.findRecruitmentByStudyId(studyId.stringRepresentation)

            if (existingRecruitment == null) {
                LOGGER.info("Recruitment for studyId $studyId is not found.")
                return@withContext null
            }

            mapWSRecruitmentToCore(existingRecruitment)
        }

    override suspend fun removeStudy(studyId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            getRecruitment(studyId) ?: return@withContext false
            recruitmentRepository.deleteByStudyId(studyId.stringRepresentation)
            LOGGER.info("Recruitment with studyId ${studyId.stringRepresentation} is deleted.")
            true
        }

    override suspend fun updateRecruitment(recruitment: Recruitment) =
        withContext(Dispatchers.IO) {
            val studyId = recruitment.studyId.stringRepresentation
            val recruitmentFound =
                recruitmentRepository.findRecruitmentByStudyId(studyId)
                    ?: throw ResourceNotFoundException("Recruitment with studyId $studyId is not found.")

            val newSnapshotNode = objectMapper.valueToTree<JsonNode>(recruitment.getSnapshot())
            recruitmentFound.snapshot = newSnapshotNode
            recruitmentRepository.save(recruitmentFound)
            LOGGER.info("Recruitment with studyId $studyId is updated.")
        }

    private fun mapWSRecruitmentToCore(recruitment: dk.cachet.carp.webservices.study.domain.Recruitment): Recruitment {
        val snapshot = objectMapper.treeToValue(recruitment.snapshot!!, RecruitmentSnapshot::class.java)
        return Recruitment.fromSnapshot(snapshot)
    }
}
