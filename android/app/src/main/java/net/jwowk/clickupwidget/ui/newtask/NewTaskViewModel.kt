package net.jwowk.clickupwidget.ui.newtask

import android.app.Application
import android.appwidget.AppWidgetManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.NewTask
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.widget.WidgetStore
import net.jwowk.clickupwidget.widget.WidgetUpdater

/** Process-lifetime scope so a create that was sent still finishes (and refreshes the widget) if the sheet is dismissed. */
internal object NewTaskWork {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

/** State and actions of the New task sheet. Compose-observable fields survive rotation with the ViewModel. */
internal class NewTaskViewModel(app: Application) : AndroidViewModel(app) {
  private var opened = false
  private var listsCache: List<ListRef>? = null

  var appWidgetId by mutableStateOf(AppWidgetManager.INVALID_APPWIDGET_ID)
    private set
  var config by mutableStateOf(WidgetConfig())
    private set

  var name by mutableStateOf("")
  var description by mutableStateOf("")
  var list by mutableStateOf<ListRef?>(null)
    private set
  var dueChoice by mutableStateOf(DueChoice.NONE)
    private set

  /** Epoch-day of the picked date (see [DueChoice.PICK]). */
  var pickedEpochDay by mutableStateOf<Long?>(null)
    private set
  var priority by mutableStateOf(Priority.NONE)
  var assignToMe by mutableStateOf(true)

  /** Statuses of the chosen list (empty until loaded / when loading failed). */
  var statuses by mutableStateOf<List<TaskStatus>>(emptyList())
    private set
  var statusesLoading by mutableStateOf(false)
    private set
  var statusesFailed by mutableStateOf(false)
    private set

  /** Chosen status name; null = the list's default (first) status. */
  var status by mutableStateOf<String?>(null)

  /** Normalized status names the widget's filter shows (empty = all). */
  private var visibleStatuses: Set<String> = emptySet()
  private var statusJob: Job? = null

  /** The chosen (or default) status would be hidden by this widget's status filter. */
  val hiddenByFilter: Boolean get() = NewTaskLogic.hiddenByFilter(status, statuses, visibleStatuses)

  var busy by mutableStateOf(false)
    private set
  var error by mutableStateOf<NewTaskError?>(null)
    private set

  /** Created successfully: the activity should close. */
  var created by mutableStateOf(false)
    private set

  val canCreate: Boolean get() = NewTaskLogic.canCreate(name, list?.id, busy)

  /**
   * Load the widget's config and the starting list. Repeat calls (rotation) are ignored unless [force]
   * (a new intent, e.g. + tapped on another widget), which also clears the form.
   */
  fun open(widgetId: Int, force: Boolean = false) {
    if (opened && (!force || busy)) return
    opened = true
    listsCache = null
    name = ""
    description = ""
    dueChoice = DueChoice.NONE
    pickedEpochDay = null
    priority = Priority.NONE
    assignToMe = true
    error = null
    status = null
    statuses = emptyList()
    appWidgetId = widgetId
    config = Graph.configStore.widgetConfig(widgetId)
    visibleStatuses = emptySet()
    viewModelScope.launch {
      visibleStatuses = runCatching { widgetVisibleStatuses(widgetId) }.getOrDefault(emptySet())
      // Re-pick the default now that the filter is known (unless the user already chose one).
      if (status == null) status = NewTaskLogic.defaultStatus(statuses, visibleStatuses)
    }
    val settings = Graph.configStore.appSettings()
    val scope = if (settings.demoMode) "demo" else config.workspaceId
    list = NewTaskLogic.initialList(config, settings, scope)
    list?.let { loadStatuses(it.id) }
    if (scope == null && list == null && !config.isListMode && settings.lastNewTaskListId.isNotBlank()) {
      // Workspace not pinned: the scope is the first workspace, which needs a lookup.
      val session = widgetId
      viewModelScope.launch {
        val resolved = runCatching { Graph.repository().workspaces().firstOrNull()?.id }.getOrNull() ?: return@launch
        if (list == null && appWidgetId == session) {
          list = NewTaskLogic.initialList(config, Graph.configStore.appSettings(), resolved)
          list?.let { loadStatuses(it.id) }
        }
      }
    }
  }

  fun chooseDue(choice: DueChoice) {
    dueChoice = choice
  }

  fun pickDate(epochDay: Long) {
    pickedEpochDay = epochDay
    dueChoice = DueChoice.PICK
  }

  fun chooseList(picked: ListRef) {
    if (picked.id != list?.id) {
      list = picked
      loadStatuses(picked.id)
    }
  }

  private fun loadStatuses(listId: String) {
    statusJob?.cancel()
    statuses = emptyList()
    status = null
    statusesFailed = false
    statusesLoading = true
    statusJob = viewModelScope.launch {
      val loaded = try {
        Graph.repository().listStatuses(listId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }
      if (list?.id != listId) return@launch
      statusesLoading = false
      statusesFailed = loaded == null
      statuses = loaded.orEmpty()
      status = NewTaskLogic.defaultStatus(statuses, visibleStatuses)
    }
  }

  /** What this widget's Status chips currently show, from its rendered state (empty = everything). */
  private suspend fun widgetVisibleStatuses(widgetId: Int): Set<String> {
    val context = getApplication<Application>()
    val glanceId = WidgetStore.glanceIdOrNull(context, widgetId) ?: return emptySet()
    val view = WidgetStore.read(context, glanceId).view(System.currentTimeMillis())
    return NewTaskLogic.visibleStatuses(view.selectedStatuses, view.presentStatuses)
  }

  /** All lists of the widget's workspace for the picker (cached for the sheet's lifetime). Throws on failure. */
  suspend fun loadLists(): List<ListRef> {
    listsCache?.let { return it }
    val repo = Graph.repository()
    val workspaceId = config.workspaceId ?: repo.workspaces().firstOrNull()?.id ?: return emptyList()
    return repo.lists(workspaceId).also { listsCache = it }
  }

  fun create() {
    val target = list ?: return
    if (!canCreate) return
    busy = true
    error = null
    val request = NewTask(
      listId = target.id,
      name = name.trim(),
      description = description.trim(),
      dueMs = NewTaskLogic.dueMs(dueChoice, pickedEpochDay, System.currentTimeMillis()),
      priority = priority,
      assignToMe = assignToMe,
      status = status,
    )
    val hidden = hiddenByFilter
    val widgetId = appWidgetId
    val rememberList = !config.isListMode
    val context = getApplication<Application>()
    NewTaskWork.scope.launch {
      try {
        Graph.repository().createTask(request)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        error = NewTaskLogic.errorFor(e)
        busy = false
        return@launch
      }
      // The task exists now: nothing below may surface an error or re-enable Create.
      created = true
      if (rememberList) {
        runCatching {
          val store = Graph.configStore
          val settings = store.appSettings()
          val scope = if (settings.demoMode) "demo"
          else config.workspaceId ?: Graph.repository().workspaces().firstOrNull()?.id
          if (scope != null) {
            store.saveAppSettings(
              settings.copy(lastNewTaskListId = target.id, lastNewTaskListName = target.name, lastNewTaskScope = scope),
            )
          }
        }
      }
      runCatching { WidgetUpdater.refreshNow(context, widgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }) }
      runCatching {
        val msg = if (hidden) R.string.newtask_created_hidden else R.string.newtask_created
        Toast.makeText(context, msg, if (hidden) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
      }
    }
  }
}
