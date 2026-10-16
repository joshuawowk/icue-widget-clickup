package net.jwowk.clickupwidget.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.jwowk.clickupwidget.R

/**
 * "Connect to ClickUp" card: explains where to find the personal API token, takes it in a
 * password-style field and verifies it. On success the token is stored (see [connectAccount])
 * and [onConnected] is called. Shared by the main screen and the widget config screen.
 */
@Composable
fun TokenEntryCard(onConnected: () -> Unit, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var token by rememberSaveable { mutableStateOf("") }
  var visible by rememberSaveable { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  val invalidMsg = stringResource(R.string.ui_error_invalid_token)
  val networkMsg = stringResource(R.string.ui_error_network)
  val serverMsg = stringResource(R.string.ui_error_server)
  val unknownMsg = stringResource(R.string.ui_error_unknown)

  fun connect() {
    if (busy || token.isBlank()) return
    busy = true
    error = null
    scope.launch {
      when (val result = connectAccount(context, token)) {
        is ConnectResult.Connected -> {
          token = ""
          onConnected()
        }
        is ConnectResult.Failed -> error = when (result.error) {
          ConnectError.INVALID_TOKEN -> invalidMsg
          ConnectError.NETWORK -> networkMsg
          ConnectError.SERVER -> serverMsg
          ConnectError.UNKNOWN -> unknownMsg
        }
      }
      busy = false
    }
  }

  SectionCard(stringResource(R.string.ui_connect_title), modifier) {
    Text(
      stringResource(R.string.ui_connect_help),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
      value = token,
      onValueChange = {
        token = it
        error = null
      },
      singleLine = true,
      enabled = !busy,
      label = { Text(stringResource(R.string.ui_token_label)) },
      placeholder = { Text("pk_…") },
      isError = error != null,
      supportingText = error?.let { msg -> { Text(msg) } },
      visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { connect() }),
      trailingIcon = {
        IconButton(onClick = { visible = !visible }) {
          Icon(
            painterResource(if (visible) R.drawable.ic_ui_visibility_off else R.drawable.ic_ui_visibility),
            contentDescription = stringResource(if (visible) R.string.ui_hide_token else R.string.ui_show_token),
          )
        }
      },
      modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(onClick = ::connect, enabled = !busy && token.isNotBlank()) {
        Text(stringResource(R.string.ui_connect))
      }
      if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
    }
    extra()
  }
}
