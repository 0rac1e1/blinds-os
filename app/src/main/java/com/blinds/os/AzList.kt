package com.blinds.os

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun letterOf(app: AppEntry): Char {
    val c = app.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

/** Every app in alphabetical order with a letter strip on the right edge to jump around. */
@Composable
fun AzList(apps: List<AppEntry>) {
    val ink = LocalInk.current
    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val firstIndex = remember(sorted) {
        // row index of each letter's header, counting one header row per letter
        val map = LinkedHashMap<Char, Int>()
        var row = 0
        var last: Char? = null
        for (a in sorted) {
            val l = letterOf(a)
            if (l != last) {
                map[l] = row
                row++
                last = l
            }
            row++
        }
        map
    }
    val letters = firstIndex.keys.toList()
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 28.dp)) {
            var last: Char? = null
            sorted.forEach { app ->
                val l = letterOf(app)
                if (l != last) {
                    last = l
                    item(key = "h$l") {
                        Txt(l.toString(), size = 14.sp, weight = FontWeight.Bold, color = ink.copy(alpha = 0.6f), modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 2.dp))
                    }
                }
                item(key = app.id) { AppRow(app) }
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(letters) {
                    fun jump(y: Float) {
                        if (letters.isEmpty()) return
                        val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.size - 1)
                        val target = firstIndex[letters[i]] ?: 0
                        scope.launch { state.scrollToItem(target) }
                    }
                    detectTapGestures(onTap = { jump(it.y) })
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        if (letters.isNotEmpty()) {
                            val i = (change.position.y / size.height * letters.size).toInt().coerceIn(0, letters.size - 1)
                            val target = firstIndex[letters[i]] ?: 0
                            scope.launch { state.scrollToItem(target) }
                        }
                    }
                },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { Txt(it.toString(), size = 11.sp, weight = FontWeight.SemiBold, color = ink.copy(alpha = 0.75f)) }
        }
    }
}
