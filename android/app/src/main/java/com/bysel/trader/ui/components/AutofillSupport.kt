package com.bysel.trader.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * Compose 1.6 Autofill bridge (Autofill in Compose).
 * ContentType semantics arrive in Compose 1.7+; until BOM bump we use [AutofillNode].
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.byselAutofill(
    vararg types: AutofillType,
    onFill: (String) -> Unit,
): Modifier = composed {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val typesList = remember(types.contentHashCode()) { types.toList() }
    val node = remember(typesList) {
        AutofillNode(
            autofillTypes = typesList,
            onFill = onFill,
        )
    }

    DisposableEffect(node) {
        autofillTree += node
        // AutofillTree in Compose 1.6 has no remove API; node is GC'd with the composition.
        onDispose { }
    }

    this
        .onGloballyPositioned { coords ->
            node.boundingBox = coords.boundsInWindow()
        }
        .onFocusChanged { focusState ->
            autofill?.run {
                if (focusState.isFocused) {
                    requestAutofillForNode(node)
                } else {
                    cancelAutofillForNode(node)
                }
            }
        }
}
