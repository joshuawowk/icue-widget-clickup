package net.jwowk.clickupwidget.ui.common

import android.content.Context
import java.io.IOException
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.widget.WidgetUpdater

/** Why connecting an account failed. */
enum class ConnectError { INVALID_TOKEN, NETWORK, SERVER, UNKNOWN }

sealed interface ConnectResult {
  /** [label] is what was stored as the account label, e.g. "Josh · Work". */
  data class Connected(val label: String) : ConnectResult
  data class Failed(val error: ConnectError, val detail: String? = null) : ConnectResult
}

/** Maps a verify failure to something the UI can word. 401 = bad token, IO = offline. */
fun connectErrorFor(t: Throwable): ConnectError = when {
  t is ClickUpApiException && t.code == 401 -> ConnectError.INVALID_TOKEN
  t is ClickUpApiException -> ConnectError.SERVER
  t is IOException -> ConnectError.NETWORK
  else -> ConnectError.UNKNOWN
}

/**
 * Verifies [rawToken] against ClickUp and, on success, stores it plus an account label and refreshes
 * all widgets. Must not be called in demo mode (the demo repository would "verify" anything).
 */
suspend fun connectAccount(context: Context, rawToken: String): ConnectResult {
  val token = rawToken.trim()
  val info = try {
    Graph.repository().verifyToken(token)
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: Exception) {
    return ConnectResult.Failed(connectErrorFor(e), e.message)
  }
  val label = listOfNotNull(info.username.ifBlank { null }, info.workspaces.firstOrNull()?.name).joinToString(" · ")
  Graph.tokenStore.save(token)
  Graph.tokenStore.saveAccountLabel(label.ifBlank { null })
  WidgetUpdater.refreshNow(context)
  return ConnectResult.Connected(label)
}

/** Forgets the token and account label and refreshes widgets (they fall back to the setup state). */
fun disconnectAccount(context: Context) {
  Graph.tokenStore.clear()
  Graph.tokenStore.saveAccountLabel(null)
  WidgetUpdater.refreshNow(context)
}
