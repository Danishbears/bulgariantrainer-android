package com.danishbears.bulgariantrainer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.transfer.ImportAction
import com.danishbears.bulgariantrainer.transfer.ImportIssue
import com.danishbears.bulgariantrainer.transfer.TransferError

@Composable internal fun actionLabel(action: ImportAction) = stringResource(when (action) {
    ImportAction.CREATE -> R.string.import_create
    ImportAction.APPEND -> R.string.import_append
    ImportAction.SKIP -> R.string.import_skip
})
@Composable internal fun issueLabel(issue: ImportIssue): String = stringResource(when (issue) {
    ImportIssue.EMPTY_RECORD -> R.string.issue_empty
    ImportIssue.NO_SEPARATOR -> R.string.issue_separator
    ImportIssue.MISSING_VALUE -> R.string.issue_missing
    ImportIssue.VALUE_TOO_LONG -> R.string.issue_length
    ImportIssue.MULTIPLE_SEPARATORS -> R.string.issue_separators
    ImportIssue.LANGUAGE_REVIEW -> R.string.issue_language
    ImportIssue.ALTERNATIVES -> R.string.issue_alternatives
    ImportIssue.COMMA_OR_LIST -> R.string.issue_comma
    ImportIssue.ANNOTATION -> R.string.issue_annotation
    ImportIssue.MULTILINE_VALUE -> R.string.issue_multiline
    ImportIssue.CONTROL_CHARACTER -> R.string.issue_control
    ImportIssue.CSV_HEADER -> R.string.issue_header
    ImportIssue.UNSUPPORTED_CSV_HEADER -> R.string.issue_bad_header
    ImportIssue.CSV_SYNTAX -> R.string.issue_csv
    ImportIssue.CSV_COLUMN_COUNT -> R.string.issue_columns
    ImportIssue.INVALID_ALTERNATIVES_JSON -> R.string.invalid_alternatives
    ImportIssue.INVALID_DIFFICULTY -> R.string.issue_difficulty
    ImportIssue.MISSING_SOURCE_ID -> R.string.issue_source_id
    ImportIssue.DUPLICATE_ALTERNATIVE -> R.string.issue_duplicate_alternative
    ImportIssue.DUPLICATE_IN_FILE -> R.string.issue_duplicate_file
    ImportIssue.DUPLICATE_IN_DATABASE -> R.string.issue_duplicate_database
    ImportIssue.INVALID_WORD -> R.string.issue_word
    ImportIssue.TARGET_CHANGED -> R.string.issue_target
    ImportIssue.TOO_MANY_TRANSLATIONS -> R.string.issue_translations
})
@Composable internal fun transferErrorLabel(error: TransferError) = stringResource(when (error) {
    TransferError.INVALID_UTF8 -> R.string.transfer_encoding_error
    TransferError.FILE_TOO_LARGE -> R.string.transfer_size_error
    TransferError.TOO_MANY_ENTRIES -> R.string.transfer_count_error
    TransferError.UNSUPPORTED_FILE -> R.string.transfer_format_error
    TransferError.INVALID_HEADER -> R.string.transfer_header_error
    TransferError.READ_FAILED -> R.string.transfer_read_error
    TransferError.WRITE_FAILED -> R.string.transfer_write_error
    TransferError.INVALID_SELECTION -> R.string.transfer_selection_error
    TransferError.TARGET_CHANGED -> R.string.issue_target
    TransferError.ALREADY_CONFIRMED -> R.string.transfer_confirmed_error
    TransferError.SENTENCE_DIFFICULTY_REQUIRED -> R.string.sentence_export_difficulty
    TransferError.STORAGE_FAILED -> R.string.transfer_storage_error
})
