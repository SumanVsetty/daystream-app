package com.ivy.planner.data

import android.net.Uri
import com.ivy.planner.domain.Collection
import com.ivy.planner.domain.CollectionColors
import com.ivy.planner.domain.CollectionType
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.Person
import com.ivy.planner.domain.TagMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Everything needed by the Journal tab, timelines, boards and search. */
data class Library(
    val entries: List<Entry>,
    val collections: List<Collection>,
    /** owner (entry or series) id → collection ids */
    val collectionsOf: Map<String, List<String>>,
    val people: List<Person>,
    /** entry id → person ids */
    val peopleOf: Map<String, List<String>>,
    /** entry id → photo file names */
    val photosOf: Map<String, List<String>>,
) {
    fun collection(id: String) = collections.firstOrNull { it.id == id }
    fun person(id: String) = people.firstOrNull { it.id == id }
}

/** Collections (boards and topics), people and photos. */
@Singleton
class LibraryRepository @Inject constructor(
    private val db: PlannerDatabase,
    private val store: AttachmentStore,
) {
    private fun newId() = UUID.randomUUID().toString()

    fun observe(): Flow<Library> {
        val base = combine(
            db.entryDao().observeAll(),
            db.collectionDao().observeAll(),
            db.collectionDao().observeLinks(),
        ) { e, c, l -> Triple(e, c, l) }
        return combine(
            base,
            db.peopleDao().observeAll(),
            db.peopleDao().observeTags(),
            db.attachmentDao().observeAll(),
        ) { (entries, collections, links), people, tags, attachments ->
            Library(
                entries = entries.map { it.toDomain() },
                collections = collections.map { it.toDomain() },
                collectionsOf = links.groupBy({ it.ownerId }, { it.collectionId }),
                people = people.map { Person(it.id, it.name, it.photoUri) },
                peopleOf = tags.groupBy({ it.entryId }, { it.personId }),
                photosOf = attachments.groupBy({ it.entryId }, { it.fileName }),
            )
        }
    }

    // ---------------------------------------------------------------- collections

    suspend fun saveCollection(name: String, type: CollectionType, id: String? = null, color: Long? = null): String {
        val all = db.collectionDao().all()
        val existing = all.firstOrNull { it.id == id }
        val cid = id ?: newId()
        db.collectionDao().upsert(
            CollectionEntity(
                id = cid,
                name = name.trim(),
                color = (color ?: existing?.color?.toLong()?.and(0xFFFFFFFFL) ?: CollectionColors.next(all.size)).toInt(),
                type = type.name,
                coverUri = existing?.coverUri,
                position = existing?.position ?: all.size,
            ),
        )
        return cid
    }

    suspend fun deleteCollection(id: String) {
        db.collectionDao().unlinkCollection(id)
        db.collectionDao().delete(id)
    }

    /** Replaces the collections of an entry or series. */
    suspend fun setCollections(ownerId: String, collectionIds: List<String>) {
        db.collectionDao().unlinkAll(ownerId)
        collectionIds.distinct().forEach { db.collectionDao().link(EntryCollectionEntity(ownerId, it)) }
    }

    suspend fun collectionsOf(ownerId: String): List<String> = db.collectionDao().collectionsOf(ownerId)

    suspend fun allCollections(): List<Collection> = db.collectionDao().all().map { it.toDomain() }

    /** Finds the board or collection for each hashtag, creating a board when none matches. */
    suspend fun resolveTags(tags: List<String>): List<String> {
        if (tags.isEmpty()) return emptyList()
        val ids = mutableListOf<String>()
        tags.forEach { tag ->
            val current = db.collectionDao().all().map { it.toDomain() }
            ids += TagMatch.find(tag, current)?.id ?: saveCollection(tag.replace('-', ' '), CollectionType.BOARD)
        }
        return ids
    }

    // ---------------------------------------------------------------- people

    suspend fun savePerson(name: String, id: String? = null): String {
        val pid = id ?: newId()
        db.peopleDao().upsert(PersonEntity(pid, name.trim()))
        return pid
    }

    suspend fun deletePerson(id: String) {
        db.peopleDao().untagPerson(id)
        db.peopleDao().delete(id)
    }

    suspend fun setPeople(entryId: String, personIds: List<String>) {
        db.peopleDao().untagAll(entryId)
        personIds.distinct().forEach { db.peopleDao().tag(EntryPersonEntity(entryId, it)) }
    }

    suspend fun peopleOf(entryId: String): List<String> = db.peopleDao().peopleOf(entryId)

    // ---------------------------------------------------------------- photos

    suspend fun photosOf(entryId: String): List<AttachmentEntity> = db.attachmentDao().forEntry(entryId)

    suspend fun addPhoto(entryId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val id = newId()
        val name = store.importPhoto(uri, id) ?: return@withContext false
        db.attachmentDao().upsert(AttachmentEntity(id, entryId, name, "image/jpeg", System.currentTimeMillis()))
        true
    }

    suspend fun removePhoto(attachmentId: String) = withContext(Dispatchers.IO) {
        db.attachmentDao().findById(attachmentId)?.let { store.delete(it.fileName) }
        db.attachmentDao().delete(attachmentId)
    }

    fun photoFile(name: String) = store.file(name)
}

fun CollectionEntity.toDomain() = Collection(
    id = id,
    name = name,
    color = color.toLong() and 0xFFFFFFFFL,
    type = if (type == CollectionType.TOPIC.name) CollectionType.TOPIC else CollectionType.BOARD,
    coverFile = coverUri,
)
