package com.danishbears.bulgariantrainer.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danishbears.bulgariantrainer.data.DuplicateWordException
import com.danishbears.bulgariantrainer.data.FieldIssue
import com.danishbears.bulgariantrainer.data.InvalidVocabularyException
import com.danishbears.bulgariantrainer.data.TranslationInput
import com.danishbears.bulgariantrainer.data.VocabularyRepository
import com.danishbears.bulgariantrainer.data.VocabularyValidator
import com.danishbears.bulgariantrainer.data.WordInput
import com.danishbears.bulgariantrainer.data.WordInputErrors
import com.danishbears.bulgariantrainer.data.WordNotFoundException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TranslationDraft(val key: String = UUID.randomUUID().toString(), val text: String = "")
data class WordDraft(
    val bulgarian: String = "",
    val translations: List<TranslationDraft> = listOf(TranslationDraft()),
    val primary: Int = 0,
    val part: String = "",
    val gender: String = "",
    val notes: String = "",
) {
    fun input() = WordInput(bulgarian, translations.mapIndexed { index, row ->
        TranslationInput(row.text, index == primary)
    }, part, gender, notes)
}

enum class EditorFailure { LOAD, SAVE, NOT_FOUND }
data class EditorState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val dirty: Boolean = false,
    val draft: WordDraft = WordDraft(),
    val errors: WordInputErrors = WordInputErrors(),
    val loadFailure: EditorFailure? = null,
    val saveFailure: EditorFailure? = null,
)

class WordEditorViewModel(
    private val repository: VocabularyRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val routeId = handle.get<String>("wordId") ?: "new"
    val wordId: Long? = routeId.toLongOrNull()
    val isNew: Boolean get() = routeId == "new"
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    init {
        if (handle.get<Boolean>("initialized") == true) {
            val texts = handle.get<ArrayList<String>>("translations") ?: arrayListOf("")
            val keys = handle.get<ArrayList<String>>("translationKeys")
            val rows = texts.mapIndexed { index, text -> TranslationDraft(keys?.getOrNull(index) ?: UUID.randomUUID().toString(), text) }
            val draft = WordDraft(handle["bulgarian"] ?: "", rows.ifEmpty { listOf(TranslationDraft()) },
                (handle.get<Int>("primary") ?: 0).coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
                handle["part"] ?: "", handle["gender"] ?: "", handle["notes"] ?: "")
            _state.value = EditorState(loading = false, draft = draft, dirty = handle["dirty"] ?: false)
        } else load()
    }

    fun load() {
        if (_state.value.saving) return
        if (isNew) {
            val draft = WordDraft()
            persist(draft, false)
            _state.value = EditorState(loading = false, draft = draft)
            return
        }
        if (wordId == null || wordId <= 0) {
            _state.value = EditorState(loading = false, loadFailure = EditorFailure.NOT_FOUND)
            return
        }
        _state.update { it.copy(loading = true, loadFailure = null) }
        viewModelScope.launch {
            try {
                val word = repository.get(wordId)
                val draft = WordDraft(word.bulgarianText,
                    word.translations.map { TranslationDraft("db:${it.id}", it.russianText) },
                    word.translations.indexOfFirst { it.isPrimary }.coerceAtLeast(0),
                    word.partOfSpeech.orEmpty(), word.grammaticalGender.orEmpty(), word.notes.orEmpty())
                persist(draft, false)
                _state.value = EditorState(loading = false, draft = draft)
            } catch (error: CancellationException) {
                throw error
            } catch (_: WordNotFoundException) {
                _state.value = EditorState(loading = false, loadFailure = EditorFailure.NOT_FOUND)
            } catch (_: Exception) {
                _state.value = EditorState(loading = false, loadFailure = EditorFailure.LOAD)
            }
        }
    }

    fun change(transform: (WordDraft) -> WordDraft) {
        val previous = _state.value
        if (previous.loading || previous.saving || previous.loadFailure != null || previous.saved) return
        val draft = transform(previous.draft)
        persist(draft, true)
        _state.value = previous.copy(draft = draft, dirty = true, saveFailure = null,
            errors = if (previous.errors.isEmpty) WordInputErrors() else VocabularyValidator.errors(draft.input()))
    }

    fun translation(key: String, value: String) = change { draft ->
        draft.copy(translations = draft.translations.map { if (it.key == key) it.copy(text = value) else it })
    }
    fun addTranslation() = change { if (it.translations.size < 50) it.copy(translations = it.translations + TranslationDraft()) else it }
    fun removeTranslation(index: Int) = change { draft ->
        if (draft.translations.size <= 1 || index !in draft.translations.indices) draft
        else draft.copy(translations = draft.translations.filterIndexed { i, _ -> i != index }, primary = when {
            index == draft.primary -> 0
            index < draft.primary -> draft.primary - 1
            else -> draft.primary
        })
    }

    fun save() {
        val current = _state.value
        if (current.loading || current.saving || current.saved || current.loadFailure != null) return
        val input = current.draft.input()
        val errors = VocabularyValidator.errors(input)
        if (!errors.isEmpty) { _state.update { it.copy(errors = errors) }; return }
        _state.update { it.copy(saving = true, saveFailure = null, errors = WordInputErrors()) }
        viewModelScope.launch {
            try {
                if (isNew) repository.create(input) else repository.update(requireNotNull(wordId), input)
                handle["dirty"] = false
                _state.update { it.copy(saved = true, dirty = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: DuplicateWordException) {
                _state.update { it.copy(errors = WordInputErrors(bulgarian = FieldIssue.DUPLICATE)) }
            } catch (error: InvalidVocabularyException) {
                _state.update { it.copy(errors = error.errors) }
            } catch (_: WordNotFoundException) {
                _state.update { it.copy(saveFailure = EditorFailure.NOT_FOUND) }
            } catch (_: Exception) {
                _state.update { it.copy(saveFailure = EditorFailure.SAVE) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    private fun persist(draft: WordDraft, dirty: Boolean) {
        handle["bulgarian"] = draft.bulgarian
        handle["translations"] = ArrayList(draft.translations.map { it.text })
        handle["translationKeys"] = ArrayList(draft.translations.map { it.key })
        handle["primary"] = draft.primary
        handle["part"] = draft.part
        handle["gender"] = draft.gender
        handle["notes"] = draft.notes
        handle["dirty"] = dirty
        handle["initialized"] = true
    }
}
