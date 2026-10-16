package net.jwowk.clickupwidget.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.CancellationException
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.model.ListRef

private sealed interface ListsState {
  data object Loading : ListsState
  data class Loaded(val lists: List<ListRef>) : ListsState
  data class Failed(val network: Boolean) : ListsState
}

/** Searchable list of every ClickUp list in the workspace (path + name). [load] fetches (and may cache) the lists. */
@Composable
fun ListPickerDialog(load: suspend () -> List<ListRef>, onPick: (ListRef) -> Unit, onDismiss: () -> Unit) {
  var state by remember { mutableStateOf<ListsState>(ListsState.Loading) }
  var attempt by remember { mutableIntStateOf(0) }
  var query by remember { mutableStateOf("") }

  LaunchedEffect(attempt) {
    state = ListsState.Loading
    state = try {
      ListsState.Loaded(load())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      ListsState.Failed(network = e is IOException)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.ui_pick_list_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          label = { Text(stringResource(R.string.ui_search)) },
          modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp), contentAlignment = Alignment.Center) {
          when (val s = state) {
            ListsState.Loading -> CircularProgressIndicator()
            is ListsState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                stringResource(if (s.network) R.string.ui_error_network else R.string.ui_error_unknown),
                color = MaterialTheme.colorScheme.error,
              )
              TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.ui_retry)) }
            }
            is ListsState.Loaded -> {
              val q = query.trim()
              val shown = if (q.isEmpty()) s.lists else s.lists.filter {
                it.name.contains(q, ignoreCase = true) || it.path.contains(q, ignoreCase = true)
              }
              if (shown.isEmpty()) {
                Text(stringResource(R.string.ui_no_lists), color = MaterialTheme.colorScheme.onSurfaceVariant)
              } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                  items(shown, key = { it.id }) { list ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(list) }.padding(vertical = 10.dp)) {
                      Text(list.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      if (list.path.isNotBlank()) {
                        Text(
                          list.path,
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                    }
                    HorizontalDivider()
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
  )
}
