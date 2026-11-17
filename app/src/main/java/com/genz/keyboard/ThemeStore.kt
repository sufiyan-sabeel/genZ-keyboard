package com.genz.keyboard

import java.io.File

/**
 * Stores user themes as one JSON file per theme under a directory.
 *
 * Takes a plain [File] rather than a `Context` so the whole store is unit-testable against a temp
 * directory — the CRUD logic is the part most likely to have bugs, and it should not need a device
 * to prove it works.
 *
 * Files are written to a temporary name and then renamed, so a crash mid-write cannot leave a
 * half-written theme that would fail to parse on the next launch.
 */
class ThemeStore(private val root: File) {

    private fun fileFor(id: String) = File(root, "$id.json")

    fun list(): List<ThemeDoc> {
        if (!root.isDirectory) return emptyList()
        return root.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { f ->
                runCatching { ThemeCodec.decode(f.readText()) }.getOrNull()
                    ?.let { if (it is ThemeValidation.Ok) it.doc else null }
            }
            .sortedBy { it.name.lowercase() }
    }

    fun load(id: String): ThemeDoc? {
        val f = fileFor(id)
        if (!f.isFile) return null
        return (ThemeCodec.decode(f.readText()) as? ThemeValidation.Ok)?.doc
    }

    fun exists(id: String): Boolean = fileFor(id).isFile

    /** Write a theme. Returns false only if the file system refused the write. */
    fun save(doc: ThemeDoc): Boolean {
        if (!root.isDirectory && !root.mkdirs()) return false
        val target = fileFor(doc.id)
        val tmp = File(root, "${doc.id}.tmp")
        return runCatching {
            tmp.writeText(ThemeCodec.encode(doc))
            // rename is atomic on the same filesystem; delete first because rename does not replace
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }.getOrDefault(false)
    }

    fun delete(id: String): Boolean {
        val f = fileFor(id)
        return f.isFile && f.delete()
    }

    /**
     * Copy a theme under a new name and a new id.
     *
     * The id is derived from content, so a duplicate must change something to differ — the name
     * does, which is why the name is part of the id seed.
     */
    fun duplicate(id: String, newName: String): ThemeDoc? {
        val original = load(id) ?: return null
        val copy = original.copy(
            id = ThemeCodec.idFor(newName, original.basePreset, original.overrides),
            name = newName
        )
        return if (save(copy)) copy else null
    }

    /** Total bytes used by stored themes — used to keep the store bounded. */
    fun sizeBytes(): Long =
        root.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
}
