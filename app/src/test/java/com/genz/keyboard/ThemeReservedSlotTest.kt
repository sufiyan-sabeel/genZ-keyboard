package com.genz.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reserved gallery slots.
 *
 * A slot exists so the gallery can say "artwork coming soon" honestly. The risk it must never carry
 * is becoming a theme: a slot with no artwork that could be applied, exported or counted as
 * installed would be a broken theme, not an empty space.
 */
class ThemeReservedSlotTest {

    @Test
    fun thereAreExactlyThreeSlots() {
        assertEquals(3, ThemePresetIndex.RESERVED_SLOTS.size)
    }

    @Test
    fun slotIdsAreStableAndDistinct() {
        assertEquals(
            listOf("slot.1", "slot.2", "slot.3"),
            ThemePresetIndex.RESERVED_SLOTS.map { it.id }
        )
        assertEquals(3, ThemePresetIndex.RESERVED_SLOTS.map { it.id }.distinct().size)
    }

    @Test
    fun slotsAreNotThemeDocuments() {
        // the whole point: a slot cannot be applied, exported or stored
        ThemePresetIndex.RESERVED_SLOTS.forEach { slot ->
            assertTrue(slot is ThemePresetIndex.ReservedSlot)
            assertFalse("a slot must not be a PresetEntry", slot is PresetEntry)
        }
    }

    @Test
    fun slotIdsCannotCollideWithRealThemes() {
        // real ids are `builtin.art.*` or `builtin.preset.*`; a slot must never match one
        ThemePresetIndex.RESERVED_SLOTS.forEach { slot ->
            assertTrue("slot id ${slot.id} would collide", slot.id.startsWith("slot."))
        }
    }

    @Test
    fun slotsAreNotGalleryFiles() {
        // nothing named slot.* is parsed as a theme file, so a slot can never be loaded
        assertFalse(ThemePresetIndex.isGalleryFile("slot.1.json"))
        assertFalse(ThemePresetIndex.isGalleryFile("slot.2.json"))
    }

    @Test
    fun aSlotSaysItHasNoArtwork() {
        ThemePresetIndex.RESERVED_SLOTS.forEach { slot ->
            assertTrue("a slot must not imply artwork exists", slot.label.contains("soon"))
        }
    }
}
