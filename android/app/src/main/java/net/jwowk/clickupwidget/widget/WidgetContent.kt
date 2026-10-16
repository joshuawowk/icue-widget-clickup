package net.jwowk.clickupwidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.LazyListScope
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.DueClass
import net.jwowk.clickupwidget.data.TaskListView
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.TaskRow
import net.jwowk.clickupwidget.data.model.LoadState
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.ui.Nav
import java.io.File

/** Which widget is rendering and whether clicks are live (false for previews). */
internal data class WidgetEnv(val appWidgetId: Int, val interactive: Boolean)

/** Per-composition sizing derived from config + LocalSize. */
private class Dims(config: WidgetConfig, widthDp: Float, heightDp: Float) {
  val bp = WidgetMath.breakpoints(widthDp, heightDp)
  val scale = WidgetMath.textScale(config.textScale, bp.narrow)
  val title = 18f * scale
  val count = 30f * scale
  val task = 15f * scale
  val meta = 12f * scale
  val chip = 11f * scale
  val circle = (20f * scale).dp
  val rowPad = config.density.rowPaddingDp.dp
  val pad = if (bp.narrow) 12.dp else 14.dp
  val contentWidth = widthDp - 2 * pad.value
  val showStar = config.showPriorityStar && !bp.narrow
  val showListName = config.showListName && !bp.narrow
  val showSubtitle = config.showSubtitle && !bp.short
}

private fun GlanceModifier.clickIf(env: WidgetEnv, action: () -> Action): GlanceModifier =
  if (env.interactive) clickable(action()) else this

private fun style(color: ColorProvider, sizeSp: Float, bold: Boolean = false, strike: Boolean = false, align: TextAlign? = null) =
  TextStyle(
    color = color,
    fontSize = sizeSp.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    textDecoration = if (strike) TextDecoration.LineThrough else null,
    textAlign = align,
  )

@Composable
private fun Icon(@DrawableRes res: Int, tint: ColorProvider, size: Dp, modifier: GlanceModifier = GlanceModifier) {
  Image(
    provider = ImageProvider(res),
    contentDescription = null,
    modifier = modifier.size(size),
    colorFilter = ColorFilter.tint(tint),
  )
}

/** The whole widget for [state]; shared by the live widget, generated previews and the config preview. */
@Composable
internal fun WidgetRoot(state: WidgetState, env: WidgetEnv) {
  val config = state.configOrDefault
  val colors = widgetColors(config)
  val size = LocalSize.current
  val dims = Dims(config, size.width.value, size.height.value)
  val corner = if (Build.VERSION.SDK_INT >= 31) {
    GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
  } else {
    GlanceModifier.cornerRadius(16.dp)
  }
  val bitmap = config.backgroundImagePath?.let { BackgroundImages.load(it) }
  Box(modifier = GlanceModifier.fillMaxSize().appWidgetBackground().then(corner)) {
    if (bitmap != null) {
      Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    }
    Column(modifier = GlanceModifier.fillMaxSize().background(colors.background).padding(dims.pad)) {
      when (state.snapshot.loadState) {
        LoadState.LOADING -> Loading(colors, dims)
        LoadState.SETUP -> Setup(colors, dims, env)
        LoadState.ERROR -> ErrorState(state, colors, dims, env)
        LoadState.READY -> Ready(state, config, colors, dims, env)
      }
    }
    ToastBar(state, colors, dims, env)
  }
}

@Composable
private fun CenterMessage(modifier: GlanceModifier, content: @Composable () -> Unit) {
  Column(
    modifier = modifier.fillMaxSize(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) { content() }
}

@Composable
private fun Loading(colors: WidgetColors, dims: Dims) {
  val context = LocalContext.current
  CenterMessage(GlanceModifier) {
    CircularProgressIndicator(modifier = GlanceModifier.size(28.dp), color = colors.accent)
    Spacer(GlanceModifier.height(8.dp))
    Text(context.getString(R.string.w_loading), style = style(colors.muted, dims.meta))
  }
}

@Composable
private fun Setup(colors: WidgetColors, dims: Dims, env: WidgetEnv) {
  val context = LocalContext.current
  CenterMessage(GlanceModifier.clickIf(env) { actionStartActivity(Nav.mainIntent(context)) }) {
    Text(
      context.getString(R.string.w_setup_title),
      style = style(colors.text, dims.task, bold = true, align = TextAlign.Center),
    )
    Spacer(GlanceModifier.height(4.dp))
    Text(context.getString(R.string.w_setup_action), style = style(colors.accent, dims.meta, bold = true))
  }
}

@Composable
private fun ErrorState(state: WidgetState, colors: WidgetColors, dims: Dims, env: WidgetEnv) {
  val context = LocalContext.current
  CenterMessage(GlanceModifier.clickIf(env) { actionRunCallback<RefreshAction>() }) {
    Icon(R.drawable.ic_w_warning, colors.error, (24f * dims.scale).dp)
    Spacer(GlanceModifier.height(6.dp))
    Text(context.getString(R.string.w_error_title), style = style(colors.text, dims.task, bold = true))
    state.snapshot.errorMessage?.let {
      Spacer(GlanceModifier.height(2.dp))
      Text(it, style = style(colors.muted, dims.meta, align = TextAlign.Center), maxLines = 3)
    }
    Spacer(GlanceModifier.height(4.dp))
    Text(
      if (state.ui.refreshing) context.getString(R.string.w_refreshing) else context.getString(R.string.w_error_retry),
      style = style(colors.accent, dims.meta, bold = true),
    )
  }
}

@Composable
private fun ColumnScope.Ready(state: WidgetState, config: WidgetConfig, colors: WidgetColors, dims: Dims, env: WidgetEnv) {
  val now = System.currentTimeMillis()
  val view = state.view(now)
  val filterEmpty = view.total == 0 && view.scopedTotal > 0
  val barOpen = state.ui.filterBarOpen || filterEmpty
  Header(state, config, view, barOpen, colors, dims, env)
  Spacer(GlanceModifier.height(8.dp))
  Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.divider)) {}
  if (view.scopedTotal == 0 && !barOpen) {
    Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) { AllClear(colors, dims) }
    return
  }
  val context = LocalContext.current
  val inFlight = state.ui.pendingCompletions + state.committing
  val toastShowing = toastText(state, now, context) != null
  LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
    if (barOpen) filterBar(view, colors, dims, env, context)
    when {
      view.scopedTotal == 0 -> item(WidgetMath.stableId("__allclear")) { AllClear(colors, dims) }
      filterEmpty -> item(WidgetMath.stableId("__filterempty")) {
        Text(
          context.getString(R.string.w_filter_empty),
          modifier = GlanceModifier.fillMaxWidth().padding(vertical = 12.dp),
          style = style(colors.muted, dims.meta, align = TextAlign.Center),
        )
      }
      else -> {
        val rows = view.visible
        rows.forEachIndexed { i, row ->
          item(WidgetMath.stableId(row.task.id)) {
            TaskRowView(row, row.task.id in inFlight, i < rows.lastIndex, config, colors, dims, env)
          }
        }
        if (view.total > rows.size) {
          item(WidgetMath.stableId("__more")) {
            Text(
              context.getString(R.string.w_more, view.total - rows.size),
              modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp),
              style = style(colors.muted, dims.meta, align = TextAlign.Center),
            )
          }
        }
      }
    }
    if (toastShowing) item(WidgetMath.stableId("__toastspace")) { Spacer(GlanceModifier.height(48.dp)) }
  }
}

@Composable
private fun Header(
  state: WidgetState,
  config: WidgetConfig,
  view: TaskListView,
  barOpen: Boolean,
  colors: WidgetColors,
  dims: Dims,
  env: WidgetEnv,
) {
  val context = LocalContext.current
  Row(
    modifier = GlanceModifier.fillMaxWidth().clickIf(env) { actionRunCallback<ToggleFilterBarAction>() },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = GlanceModifier.defaultWeight()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(config.title.ifBlank { "My Tasks" }, style = style(colors.text, dims.title, bold = true), maxLines = 1)
        Spacer(GlanceModifier.width(4.dp))
        Icon(if (barOpen) R.drawable.ic_w_chevron_up else R.drawable.ic_w_chevron_down, colors.accent, (18f * dims.scale).dp)
        if (view.anyConstrained) {
          Spacer(GlanceModifier.width(3.dp))
          Icon(R.drawable.ic_w_dot, colors.accent, 7.dp)
        }
      }
      if (dims.showSubtitle) {
        val sub = if (state.ui.refreshing) {
          context.getString(R.string.w_refreshing)
        } else {
          listOfNotNull(
            state.snapshot.workspaceName.takeIf { it.isNotBlank() },
            context.getString(R.string.w_filtered).takeIf { view.anyConstrained },
            when {
              !state.snapshot.offline -> null
              state.snapshot.authError -> context.getString(R.string.w_token_invalid)
              else -> context.getString(R.string.w_offline)
            },
          ).joinToString(" • ")
        }
        if (sub.isNotEmpty()) Text(sub, style = style(colors.muted, dims.meta), maxLines = 1)
      }
    }
    if (config.showCount) {
      Spacer(GlanceModifier.width(8.dp))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(view.total.toString(), style = style(colors.accent, dims.count, bold = true), maxLines = 1)
        // Narrow widgets drop the "tasks" word so the title keeps its room.
        if (!dims.bp.narrow) {
          Spacer(GlanceModifier.width(4.dp))
          Text(
            context.getString(if (view.total == 1) R.string.w_task_one else R.string.w_task_many),
            modifier = GlanceModifier.padding(bottom = (4f * dims.scale).dp),
            style = style(colors.muted, dims.meta),
          )
        }
      }
    }
  }
}

@Composable
private fun AllClear(colors: WidgetColors, dims: Dims) {
  val context = LocalContext.current
  Column(
    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(R.drawable.ic_w_check_circle, colors.ok, (32f * dims.scale).dp)
    Spacer(GlanceModifier.height(6.dp))
    Text(context.getString(R.string.w_all_clear), style = style(colors.text, dims.task, bold = true))
    Text(context.getString(R.string.w_no_matching), style = style(colors.muted, dims.meta))
  }
}

private class ChipSpec(val label: String, val argb: Long, val on: Boolean, val action: Action) {
  val color: ColorProvider get() = ColorProvider(Color(argb))
  val fadedColor: ColorProvider get() = ColorProvider(Color(argb).copy(alpha = 0.45f))
}

private fun LazyListScope.filterBar(view: TaskListView, colors: WidgetColors, dims: Dims, env: WidgetEnv, context: Context) {
  fun section(key: String, label: String, chips: List<ChipSpec>) {
    if (chips.isEmpty()) return
    item(WidgetMath.stableId("__label_$key")) {
      Text(
        label,
        modifier = GlanceModifier.padding(top = 8.dp, bottom = 4.dp),
        style = style(colors.muted, dims.chip, bold = true),
      )
    }
    val widths = chips.map { WidgetMath.chipWidthDp(it.label, dims.chip) }
    WidgetMath.wrapRows(widths, dims.contentWidth).forEachIndexed { r, idx ->
      item(WidgetMath.stableId("__chips_${key}_$r")) {
        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
          idx.forEach { i -> Chip(chips[i], colors, dims, env) }
        }
      }
    }
  }
  section(
    "status", context.getString(R.string.w_status),
    view.presentStatuses.map { s ->
      ChipSpec(
        s.name, TaskLogic.parseColor(s.colorHex), s.name in view.selectedStatuses,
        actionRunCallback<ToggleStatusAction>(actionParametersOf(WidgetParams.STATUS to s.name)),
      )
    },
  )
  section(
    "priority", context.getString(R.string.w_priority),
    view.presentPriorities.map { p ->
      ChipSpec(
        p.label, p.chipColor, p.key in view.selectedPriorities,
        actionRunCallback<TogglePriorityAction>(actionParametersOf(WidgetParams.PRIORITY to p.key)),
      )
    },
  )
  item(WidgetMath.stableId("__actions")) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
      Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        ActionPill(R.drawable.ic_w_refresh, context.getString(R.string.w_refresh), colors, dims, env) {
          actionRunCallback<RefreshAction>()
        }
        ActionPill(R.drawable.ic_w_settings, context.getString(R.string.w_settings), colors, dims, env) {
          actionStartActivity(Nav.configIntent(context, env.appWidgetId))
        }
        if (!dims.bp.narrow) {
          ActionPill(R.drawable.ic_w_open, context.getString(R.string.w_open_clickup), colors, dims, env) {
            actionStartActivity(
              Intent(Intent.ACTION_VIEW, Uri.parse("https://app.clickup.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
          }
        }
      }
      Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.rowDivider)) {}
    }
  }
}

@Composable
private fun Chip(chip: ChipSpec, colors: WidgetColors, dims: Dims, env: WidgetEnv) {
  Box(GlanceModifier.padding(end = 4.dp)) {
    Row(
      modifier = GlanceModifier
        .background(
          ImageProvider(if (chip.on) R.drawable.widget_chip_on else R.drawable.widget_chip_off),
          colorFilter = ColorFilter.tint(if (chip.on) chip.color else colors.faded),
        )
        .padding(horizontal = 8.dp, vertical = 3.dp)
        .clickIf(env) { chip.action },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(R.drawable.ic_w_dot, if (chip.on) chip.color else chip.fadedColor, 7.dp)
      Spacer(GlanceModifier.width(5.dp))
      Text(chip.label, style = style(if (chip.on) colors.text else colors.faded, dims.chip, bold = chip.on), maxLines = 1)
    }
  }
}

@Composable
private fun ActionPill(
  @DrawableRes icon: Int,
  label: String,
  colors: WidgetColors,
  dims: Dims,
  env: WidgetEnv,
  action: () -> Action,
) {
  Box(GlanceModifier.padding(end = 6.dp)) {
    Row(
      modifier = GlanceModifier
        .background(ImageProvider(R.drawable.widget_chip_off), colorFilter = ColorFilter.tint(colors.accent))
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .clickIf(env, action),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, colors.accent, (13f * dims.scale).dp)
      Spacer(GlanceModifier.width(4.dp))
      Text(label, style = style(colors.accent, dims.chip, bold = true), maxLines = 1)
    }
  }
}

@Composable
private fun TaskRowView(
  row: TaskRow,
  inFlight: Boolean,
  divider: Boolean,
  config: WidgetConfig,
  colors: WidgetColors,
  dims: Dims,
  env: WidgetEnv,
) {
  val context = LocalContext.current
  val task = row.task
  Column(modifier = GlanceModifier.fillMaxWidth()) {
    Row(
      modifier = GlanceModifier.fillMaxWidth().padding(vertical = dims.rowPad)
        .clickIf(env) { actionStartActivity(Nav.detailIntent(context, env.appWidgetId, task)) },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = GlanceModifier.padding(end = 10.dp).clickIf(env) {
          actionRunCallback<CompleteTaskAction>(
            actionParametersOf(
              WidgetParams.TASK_ID to task.id,
              *listOfNotNull(task.listId?.let { WidgetParams.LIST_ID to it }).toTypedArray(),
            ),
          )
        },
      ) {
        Image(
          provider = ImageProvider(if (inFlight) R.drawable.ic_w_check_circle else R.drawable.ic_w_circle),
          contentDescription = context.getString(if (inFlight) R.string.w_cd_completing else R.string.w_cd_complete),
          modifier = GlanceModifier.size(dims.circle),
          colorFilter = ColorFilter.tint(colors.accent),
        )
      }
      Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
          task.name,
          style = style(if (inFlight) colors.muted else colors.text, dims.task, strike = inFlight),
          maxLines = 2,
        )
        val listName = task.listName.takeIf { dims.showListName && it.isNotBlank() }
        val due = row.due.takeIf { config.showDueChip }
        if (listName != null || due != null) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (listName != null) {
              Text(WidgetMath.ellipsize(listName, 24), style = style(colors.muted, dims.meta), maxLines = 1)
            }
            if (due != null) {
              if (listName != null) Spacer(GlanceModifier.width(8.dp))
              val dueColor = when (due.cls) {
                DueClass.OVERDUE -> colors.overdue
                DueClass.TODAY -> colors.today
                DueClass.SOON -> colors.soon
                DueClass.LATER -> colors.muted
              }
              Icon(R.drawable.ic_w_calendar, dueColor, (dims.meta * 0.95f).dp)
              Spacer(GlanceModifier.width(3.dp))
              Text(due.label, style = style(dueColor, dims.meta, bold = true), maxLines = 1)
            }
          }
        }
      }
      if (dims.showStar) {
        Spacer(GlanceModifier.width(6.dp))
        if (task.priority.starred) {
          Icon(R.drawable.ic_w_star, colors.accent, (16f * dims.scale).dp)
        } else {
          Icon(R.drawable.ic_w_star_outline, colors.starOff, (16f * dims.scale).dp)
        }
      }
    }
    if (divider) Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.rowDivider)) {}
  }
}

private fun toastText(state: WidgetState, now: Long, context: Context): String? {
  val pending = state.ui.pendingCompletions
  return when {
    pending.size == 1 -> {
      val name = state.snapshot.tasks.firstOrNull { it.id in pending }?.name.orEmpty()
      context.getString(R.string.w_completing_one, WidgetMath.ellipsize(name, 28))
    }
    pending.size > 1 -> context.getString(R.string.w_completing_many, pending.size)
    state.ui.toast != null && state.ui.toastUntilMs > now -> state.ui.toast
    else -> null
  }
}

@Composable
private fun ToastBar(state: WidgetState, colors: WidgetColors, dims: Dims, env: WidgetEnv) {
  val context = LocalContext.current
  val text = toastText(state, System.currentTimeMillis(), context) ?: return
  val undo = state.ui.pendingCompletions.isNotEmpty()
  Box(modifier = GlanceModifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.BottomCenter) {
    Row(
      modifier = GlanceModifier.fillMaxWidth()
        .background(ImageProvider(R.drawable.widget_pill), colorFilter = ColorFilter.tint(colors.toastBackground))
        .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text, modifier = GlanceModifier.defaultWeight(), style = style(colors.toastText, dims.meta), maxLines = 1)
      if (undo) {
        Spacer(GlanceModifier.width(8.dp))
        Text(
          context.getString(R.string.w_undo),
          modifier = GlanceModifier.clickIf(env) { actionRunCallback<UndoAction>() },
          style = style(colors.toastAction, dims.meta, bold = true),
        )
      }
    }
  }
}

/** Decodes the configured background image once per path + mtime (≤ ~720 px long edge). */
private object BackgroundImages {
  private const val MAX_EDGE = 720
  private var key: String? = null
  private var cached: Bitmap? = null

  @Synchronized
  fun load(path: String): Bitmap? {
    val file = File(path)
    if (!file.isFile) return null
    val k = "$path@${file.lastModified()}"
    if (k == key) return cached
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val bitmap = runCatching {
      BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = WidgetMath.sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE) },
      )
    }.getOrNull()
    key = k
    cached = bitmap
    return bitmap
  }
}
