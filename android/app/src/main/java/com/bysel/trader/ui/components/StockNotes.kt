package com.bysel.trader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bysel.trader.data.repository.StockNotesRepository
import com.bysel.trader.data.stockNoteDisplayBase
import com.bysel.trader.ui.theme.LocalAppTheme
import com.bysel.trader.ui.viewmodel.StockNotesViewModel

@Composable
fun StockNotesIcon(
    symbol: String,
    modifier: Modifier = Modifier,
    displaySymbol: String = symbol,
) {
    if (symbol.isBlank()) return
    val notesViewModel: StockNotesViewModel = viewModel()
    val notes by notesViewModel.notes.collectAsStateWithLifecycle()
    val hasNote = remember(notes, symbol) { notesViewModel.hasNote(symbol, notes) }
    var showSheet by remember(symbol) { mutableStateOf(false) }
    val theme = LocalAppTheme.current

    IconButton(
        onClick = { showSheet = true },
        modifier = modifier.size(28.dp),
    ) {
        BadgedBox(
            badge = {
                if (hasNote) {
                    Badge(containerColor = theme.primary)
                }
            },
        ) {
            Icon(
                imageVector = if (hasNote) Icons.Filled.StickyNote2 else Icons.Outlined.StickyNote2,
                contentDescription = if (hasNote) {
                    "Edit notes for $displaySymbol"
                } else {
                    "Add notes for $displaySymbol"
                },
                tint = if (hasNote) theme.primary else theme.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (showSheet) {
        StockNotesSheet(
            symbol = symbol,
            displaySymbol = displaySymbol,
            onDismiss = { showSheet = false },
            viewModel = notesViewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockNotesSheet(
    symbol: String,
    onDismiss: () -> Unit,
    displaySymbol: String = symbol,
    viewModel: StockNotesViewModel = viewModel(),
) {
    val theme = LocalAppTheme.current
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val existing = remember(notes, symbol) { viewModel.noteText(symbol, notes) }
    var draft by rememberSaveable(symbol, existing) { mutableStateOf(existing) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val label = stockNoteDisplayBase(displaySymbol).ifBlank { displaySymbol }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = displaySymbol.ifBlank { label },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = theme.text,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(StockNotesRepository.MAX_NOTE_CHARS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                minLines = 4,
                maxLines = 8,
                placeholder = {
                    Text("Your notes for $label — not visible to others.")
                },
                colors = appOutlinedTextFieldColors(),
            )
            Text(
                text = "Private to you. Not investment advice.",
                fontSize = 11.sp,
                color = theme.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        draft = ""
                        viewModel.clear(symbol)
                        onDismiss()
                    },
                    enabled = existing.isNotBlank() || draft.isNotBlank(),
                ) {
                    Text("Clear", color = theme.negative)
                }
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = theme.textSecondary)
                    }
                    TextButton(
                        onClick = {
                            viewModel.save(symbol, draft)
                            onDismiss()
                        },
                    ) {
                        Text("Save", color = theme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
