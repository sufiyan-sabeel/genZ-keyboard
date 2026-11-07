package com.genz.keyboard

import java.io.File

/**
 * Access to the dictionary files bundled under `app/src/main/assets`.
 *
 * The rare-word lists are split into parts so that no single asset file becomes unwieldy
 * (`ru_more.txt` + `ru_more2.txt`). Tests must read every part, in order, or they only see
 * the first slice of the alphabet.
 */
internal object TestAssets {

    private fun dictDir() = File("src/main/assets/dict")

    /** All lines of every part of the rare-word list for [code], in file order. */
    fun more(code: String): List<String> =
        listOf("${code}_more.txt", "${code}_more2.txt")
            .map { File(dictDir(), it) }
            .filter { it.exists() }
            .flatMap { it.readLines() }

    /** The main word list for [code]. */
    fun words(code: String): List<String> =
        File(dictDir(), "$code.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
}
