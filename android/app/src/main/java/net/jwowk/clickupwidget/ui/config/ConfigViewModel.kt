package net.jwowk.clickupwidget.ui.config

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.Workspace

/** Editing state for one widget's settings; survives rotation. Nothing is persisted until [commit]. */
class ConfigViewModel(app: Application, val appWidgetId: Int) : AndroidViewModel(app) {
  private val store = Graph.configStore
  private val saved: WidgetConfig = store.widgetConfig(appWidgetId)

  /** The edited config. In single-list mode [WidgetConfig.listId] holds the chosen list; see [listMode]. */
  var config by mutableStateOf(saved)
    private set

  /** Source mode toggle; kept apart from [WidgetConfig.listId] so switching back and forth keeps the chosen list. */
  var listMode by mutableStateOf(saved.isListMode)
    private set

  var listError by mutableStateOf(false)
    private set

  var workspaces by mutableStateOf<List<Workspace>>(emptyList())
    private set

  /** No token and not demo mode: the screen shows the token-entry card. */
  var needsSetup by mutableStateOf(computeNeedsSetup())
    private set

  var blurRadius by mutableIntStateOf(saved.backgroundBlur)
    private set

  var imageBusy by mutableStateOf(false)
    private set

  var imageFailed by mutableStateOf(false)
    private set

  private var sourceUri: Uri? = null
  private var imageJob: Job? = null
  private var editVersion = 0
  private var committed = false
  private val listsCache = mutableMapOf<String, List<ListRef>>()

  /** The blur slider can re-process only while the picked image is still known (this session). */
  val canReblur: Boolean get() = sourceUri != null

  init {
    loadWorkspaces()
  }

  /** The config as it will be saved / previewed (single-list fields cleared in My Tasks mode). */
  fun effectiveConfig(): WidgetConfig =
    if (listMode) config else config.copy(listId = "", listName = "")

  fun update(transform: (WidgetConfig) -> WidgetConfig) {
    config = transform(config)
  }

  fun selectListMode(enabled: Boolean) {
    listMode = enabled
    if (!enabled) listError = false
  }

  fun setList(id: String, name: String) {
    listError = false
    update { it.copy(listId = id.trim(), listName = name, title = titleForList(it.title, it.listName, name)) }
  }

  fun setWorkspace(id: String?) {
    // Lists belong to a workspace, so a previously chosen list no longer applies.
    update { it.copy(workspaceId = id, listId = "", listName = "") }
  }

  /** Name of the selected (or first) workspace, for the preview subtitle; null if unknown. */
  fun selectedWorkspace(): Workspace? =
    workspaces.firstOrNull { it.id == config.workspaceId } ?: workspaces.firstOrNull()

  /** Re-evaluate token/demo state after connecting or switching to sample tasks, and reload workspaces. */
  fun accountChanged() {
    needsSetup = computeNeedsSetup()
    listsCache.clear()
    loadWorkspaces()
  }

  fun useSampleTasks() {
    val settings = Graph.configStore.appSettings()
    Graph.configStore.saveAppSettings(settings.copy(demoMode = true))
    accountChanged()
  }

  private fun computeNeedsSetup(): Boolean =
    !Graph.tokenStore.hasToken() && !Graph.configStore.appSettings().demoMode

  private fun loadWorkspaces() {
    if (needsSetup) {
      workspaces = emptyList()
      return
    }
    viewModelScope.launch {
      workspaces = runCatching { Graph.repository().workspaces() }.getOrDefault(emptyList())
    }
  }

  /** All lists of the selected workspace for the picker (cached for the session). Throws on failure. */
  suspend fun loadLists(): List<ListRef> {
    val repo = Graph.repository()
    val workspaceId = config.workspaceId ?: workspaces.firstOrNull()?.id ?: repo.workspaces().firstOrNull()?.id
      ?: return emptyList()
    return listsCache[workspaceId] ?: repo.lists(workspaceId).also { listsCache[workspaceId] = it }
  }

  // ---- Background image ----

  fun pickImage(uri: Uri) {
    sourceUri = uri
    renderImage(blurRadius)
  }

  fun setBlur(radius: Int) {
    blurRadius = radius
  }

  /** Re-processes the picked image with the current blur (call when the slider is released). */
  fun applyBlur() {
    if (sourceUri != null) renderImage(blurRadius)
  }

  fun removeImage() {
    imageJob?.cancel()
    imageBusy = false
    imageFailed = false
    sourceUri = null
    blurRadius = 0
    config = config.copy(backgroundImagePath = null, backgroundBlur = 0)
  }

  private fun renderImage(radius: Int) {
    val uri = sourceUri ?: return
    val previous = imageJob
    previous?.cancel()
    val target = BackgroundImages.editFile(getApplication(), appWidgetId, ++editVersion)
    imageJob = viewModelScope.launch {
      imageBusy = true
      imageFailed = false
      previous?.join()
      val ok = BackgroundImages.render(getApplication(), uri, radius, target)
      imageBusy = false
      if (ok) {
        val old = config.backgroundImagePath?.let(::File)
        config = config.copy(backgroundImagePath = target.absolutePath, backgroundBlur = radius)
        if (old != null && old.name.contains("_edit")) old.delete()
      } else {
        target.delete()
        imageFailed = true
      }
    }
  }

  /** Reset every style option (and the background image) to defaults; source settings are kept. */
  fun resetStyle() {
    val d = WidgetConfig()
    imageJob?.cancel()
    imageBusy = false
    imageFailed = false
    sourceUri = null
    blurRadius = 0
    config = config.copy(
      theme = d.theme, textColor = d.textColor, accentColor = d.accentColor, backgroundColor = d.backgroundColor,
      backgroundOpacity = d.backgroundOpacity, textScale = d.textScale, density = d.density,
      showSubtitle = d.showSubtitle, showCount = d.showCount, showListName = d.showListName,
      showDueChip = d.showDueChip, showPriorityStar = d.showPriorityStar,
      showPriority = d.showPriority, showTags = d.showTags,
      backgroundImagePath = null, backgroundBlur = 0,
    )
  }

  /**
   * Validates and finalizes the edited config for saving: normalizes fields and promotes the scratch
   * background image to `widget_bg_<id>.jpg`. Returns null (and flags [listError]) if single-list mode has no list.
   */
  suspend fun commit(): WidgetConfig? {
    if (listMode && config.listId.isBlank()) {
      listError = true
      return null
    }
    imageJob?.join()
    var result = effectiveConfig().copy(
      title = config.title.trim().ifBlank { "My Tasks" },
      statusDefault = config.statusDefault.trim(),
      maxTasks = config.maxTasks.coerceIn(WidgetConfig.MIN_TASKS, WidgetConfig.MAX_TASKS),
      backgroundOpacity = config.backgroundOpacity.coerceIn(0, 100),
      textScale = config.textScale.coerceIn(80, 140),
    )
    val context = getApplication<Application>()
    result = withContext(Dispatchers.IO) {
      val path = result.backgroundImagePath
      val finalFile = BackgroundImages.finalFile(context, appWidgetId)
      when {
        path == null -> {
          if (saved.backgroundImagePath != null) File(saved.backgroundImagePath).delete()
          finalFile.delete()
          BackgroundImages.deleteEditFiles(context, appWidgetId)
          result
        }
        path != finalFile.absolutePath -> {
          val source = File(path)
          finalFile.delete()
          // Rename, else copy; on total failure keep pointing at the (existing) scratch file.
          val promoted = source.renameTo(finalFile) ||
            runCatching { source.copyTo(finalFile, overwrite = true) }.isSuccess && finalFile.exists()
          if (promoted) {
            BackgroundImages.deleteEditFiles(context, appWidgetId)
            result.copy(backgroundImagePath = finalFile.absolutePath)
          } else {
            finalFile.delete()
            result
          }
        }
        else -> result
      }
    }
    // Keep the edited config in step so a retried commit sees the promoted file, not the deleted scratch one.
    config = config.copy(backgroundImagePath = result.backgroundImagePath)
    committed = true
    return result
  }

  override fun onCleared() {
    if (!committed) BackgroundImages.deleteEditFiles(getApplication(), appWidgetId)
  }
}

/** New title after picking a list: the list's name if the title is blank/default/the previous list's name, else unchanged. */
internal fun titleForList(title: String, previousListName: String, newListName: String): String {
  val name = newListName.trim()
  if (name.isEmpty()) return title
  val t = title.trim()
  return if (t.isEmpty() || t == "My Tasks" || (previousListName.isNotBlank() && t == previousListName.trim())) name else title
}
