package com.climbtracker.data

import com.climbtracker.core.editor.HoldRole
import com.climbtracker.core.tracker.AttemptResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Whole-app backup as a zip: `climbtracker.json` with every table and the wall photos under
 * `walls/`. Importing replaces everything the app holds.
 */
class Backup(private val dao: ClimbDao, private val photoDir: File) {

    suspend fun export(out: OutputStream) {
        val walls = dao.allWallsOnce()
        val json = JSONObject()
            .put("format", FORMAT)
            .put("walls", array(walls) { w ->
                JSONObject().put("id", w.id).put("photo", File(w.photoPath).name).put("width", w.width)
                    .put("height", w.height).put("createdAt", w.createdAt).put("gymId", w.gymId ?: JSONObject.NULL)
            })
            .put("gyms", array(dao.allGymsOnce()) { g ->
                JSONObject().put("id", g.id).put("name", g.name).put("createdAt", g.createdAt)
                    .put("latitude", g.latitude ?: JSONObject.NULL).put("longitude", g.longitude ?: JSONObject.NULL)
            })
            .put("holds", array(dao.allHoldsOnce()) { h ->
                JSONObject().put("id", h.id).put("wallId", h.wallId).put("contour", h.contour).put("argb", h.argb)
                    .put("colorGroup", h.colorGroup).put("manual", h.manual)
            })
            .put("boulders", array(dao.allBouldersOnce()) { b ->
                JSONObject().put("id", b.id).put("wallId", b.wallId).put("name", b.name).put("grade", b.grade)
                    .put("notes", b.notes).put("createdAt", b.createdAt).put("archived", b.archived)
            })
            .put("boulderHolds", array(dao.allBoulderHoldsOnce()) { l ->
                JSONObject().put("boulderId", l.boulderId).put("holdId", l.holdId).put("role", l.role.name)
                    .put("markOrder", l.markOrder)
            })
            .put("attempts", array(dao.allAttemptsOnce()) { a ->
                JSONObject().put("id", a.id).put("boulderId", a.boulderId).put("date", a.date)
                    .put("result", a.result.name).put("lastHoldId", a.lastHoldId ?: JSONObject.NULL)
            })
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (wall in walls) {
                val photo = File(wall.photoPath)
                if (!photo.exists()) continue
                zip.putNextEntry(ZipEntry(PHOTOS + photo.name))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Replaces all data and photos with those of the backup read from [input].
     * Throws IllegalArgumentException, leaving everything as it was, if it is not a backup.
     */
    suspend fun import(input: InputStream) {
        val staging = File(photoDir.parentFile, "import-staging").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            var text: String? = null
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == DATA -> text = zip.readBytes().toString(Charsets.UTF_8)
                        // Only the file name is used, so an entry cannot write outside the folder.
                        entry.name.startsWith(PHOTOS) && !entry.isDirectory ->
                            File(staging, File(entry.name).name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val json = try {
                JSONObject(requireNotNull(text) { "The file is not a Climb Tracker backup" })
            } catch (e: JSONException) {
                throw IllegalArgumentException("The backup data cannot be read", e)
            }
            require(json.optInt("format") == FORMAT) { "Unsupported backup format" }

            // Absent in backups made before gyms existed.
            var gyms: List<GymEntity> = emptyList()
            val walls: List<WallEntity>
            val holds: List<HoldEntity>
            val boulders: List<BoulderEntity>
            val links: List<BoulderHoldEntity>
            val attempts: List<AttemptEntity>
            try {
                if (json.has("gyms")) {
                    gyms = list(json, "gyms") { o -> GymEntity(
                            o.getLong("id"), o.getString("name"), o.getLong("createdAt"),
                            if (o.isNull("latitude")) null else o.getDouble("latitude"),
                            if (o.isNull("longitude")) null else o.getDouble("longitude"),
                        )
                    }
                }
                walls = list(json, "walls") { o ->
                    WallEntity(o.getLong("id"), File(photoDir, File(o.getString("photo")).name).path, o.getInt("width"), o.getInt("height"), o.getLong("createdAt"), if (o.isNull("gymId")) null else o.getLong("gymId"))
                }
                holds = list(json, "holds") { o ->
                    HoldEntity(o.getLong("id"), o.getLong("wallId"), o.getString("contour"), o.getInt("argb"), o.getInt("colorGroup"), o.getBoolean("manual"))
                }
                boulders = list(json, "boulders") { o ->
                    BoulderEntity(o.getLong("id"), o.getLong("wallId"), o.getString("name"), o.getString("grade"), o.getString("notes"), o.getLong("createdAt"), o.optBoolean("archived"))
                }
                links = list(json, "boulderHolds") { o ->
                    BoulderHoldEntity(o.getLong("boulderId"), o.getLong("holdId"), HoldRole.valueOf(o.getString("role")), o.getInt("markOrder"))
                }
                attempts = list(json, "attempts") { o ->
                    AttemptEntity(o.getLong("id"), o.getLong("boulderId"), o.getLong("date"), AttemptResult.valueOf(o.getString("result")), if (o.isNull("lastHoldId")) null else o.getLong("lastHoldId"))
                }
            } catch (e: JSONException) {
                throw IllegalArgumentException("The backup data is incomplete", e)
            }

            dao.restore(gyms, walls, holds, boulders, links, attempts)

            photoDir.mkdirs()
            photoDir.listFiles()?.forEach { it.delete() }
            staging.listFiles()?.forEach { it.renameTo(File(photoDir, it.name)) }
        } catch (e: java.util.zip.ZipException) {
            throw IllegalArgumentException("The file is not a zip archive", e)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun <T> array(items: List<T>, map: (T) -> JSONObject): JSONArray = JSONArray().also { a -> items.forEach { a.put(map(it)) } }

    private fun <T> list(json: JSONObject, key: String, map: (JSONObject) -> T): List<T> {
        val array = json.getJSONArray(key)
        return List(array.length()) { map(array.getJSONObject(it)) }
    }

    private companion object {
        const val FORMAT = 1
        const val DATA = "climbtracker.json"
        const val PHOTOS = "walls/"
    }
}
