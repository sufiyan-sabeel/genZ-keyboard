package com.genz.keyboard

/**
 * A no-op [KeyboardActions] for the Theme Studio preview.
 *
 * The preview renders the production `KeyArea`, which requires actions. Nothing in the preview may
 * reach the editor, so every call is inert — this is a rendering stand-in, not a second keyboard
 * implementation. The key *visuals* come from the real `KeyView` and the real palette.
 */
internal object PreviewActions : KeyboardActions {
    override fun keyDown(key: KeyModel) {}
    override fun keyUp() {}
    override fun keyTick() {}
    override fun commitKey(key: KeyModel, text: String) {}
    override fun swipeSpace(direction: Int) {}
    override fun pickSuggestion(chip: SuggestionChipModel) {}
    override fun commitText(text: String) {}
    override fun commitEmoji(emoji: String) {}
    override fun deleteBackward() {}
    override fun commitGif(gif: TenorGifResult) {}
    override fun headerMenu() {}
    override fun togglePanel(panel: ActivePanel) {}
    override fun toggleVoice() {}
    override fun openSettings() {}
    override fun openVoiceFallback() {}
    override fun hideKeyboard() {}
    override fun clearClipboard() {}
    override fun insertTranslation() {}
    override fun commitSymbol(text: String) {}
    override fun runPluginAction(pluginId: String, index: Int) {}
    override fun replaceLast(old: String, new: String) {}
    override fun commitGlide(path: List<androidx.compose.ui.geometry.Offset>, keys: Map<Char, androidx.compose.ui.geometry.Offset>, keyWidth: Float, tapLetter: String?) {}
    override fun switchKeyboard() {}
    override fun runScriptButton(pluginId: String, id: String) {}
    override fun removeClip(text: String) {}
    override fun togglePinClip(text: String) {}
    override fun forgetSuggestion(word: String) {}
}
