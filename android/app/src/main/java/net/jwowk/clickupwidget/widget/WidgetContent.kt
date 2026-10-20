package net.jwowk.clickupwidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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
import net.jwowk.clickupwidget.data.DueInfo
import net.jwowk.clickupwidget.data.TaskListView
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.TaskRow
import net.jwowk.clickupwidget.data.model.LoadState
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.style.Palettes
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.ui.Nav
import java.io.File

/** Which widget is rendering and whether clicks are live (false for previews). */
internal data class WidgetEnv(val appWidgetId: Int, val interactive: Boolean)

/**
 * Measures with the platform default font, which the widget's text views use. [SLACK] keeps a margin for
 * launchers/OEM fonts that render a little wider; an underestimate would clip the last part of a row line.
 */
private class PaintMeasure(private val fontScale: Float) : WidgetMath.TextMeasure {
  private val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
  private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

  override fun widthDp(text: String, sp: Float, bold: Boolean): Float {
    val paint = if (bold) this.bold else regular
    // Text size in dp units (sp × font scale), so measureText answers in dp too.
    paint.textSize = sp * fontScale
    return paint.measureText(text) * SLACK
  }

  companion object {
    const val SLACK = 1.05f
  }
}

/** Per-composition sizing derived from config + LocalSize (+ the system font scale, for text measurement). */
private class Dims(config: WidgetConfig, widthDp: Float, heightDp: Float, fontScale: Float) {
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
  val showRequester = config.showRequester && !bp.narrow
  val showSubtitle = config.showSubtitle && !bp.short
  /** Estimated width of a row's text column: content minus check circle (+ gap), star (+ gap) and slack. */
  val textWidth = contentWidth - circle.value - 10f - (if (showStar) 6f + 16f * scale else 0f) - 4f
  val measure: WidgetMath.TextMeasure = PaintMeasure(fontScale)
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
  val dims = Dims(config, size.width.value, size.height.value, LocalContext.current.resources.configuration.fontScale)
  val corner = if (Build.VERSION.SDK_INT >= 31) {
    GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
  } else {
    GlanceModifier.cornerRadius(16.dp)
  }
  // iCUE stacks the image over the background color and fades the whole stack with the transparency
  // setting; an opaque image hides the color, so draw just the image with the opacity baked in.
  val bitmap = config.backgroundImagePath?.let { BackgroundImages.load(it, config.backgroundOpacity) }
  Box(modifier = GlanceModifier.fillMaxSize().appWidgetBackground().then(corner)) {
    if (bitmap != null) {
      Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    }
    val content = GlanceModifier.fillMaxSize().let { if (bitmap == null) it.background(colors.background) else it }
    Column(modifier = content) {
      val padded = GlanceModifier.fillMaxSize().padding(dims.pad)
      when (state.snapshot.loadState) {
        LoadState.LOADING -> Column(padded) { Loading(colors, dims) }
        LoadState.SETUP -> Column(padded) { Setup(colors, dims, env) }
        LoadState.ERROR -> Column(padded) { ErrorState(state, colors, dims, env) }
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
  val auth = state.snapshot.authError
  CenterMessage(
    GlanceModifier.clickIf(env) { if (auth) actionStartActivity(Nav.mainIntent(context)) else actionRunCallback<RefreshAction>() },
  ) {
    Icon(R.drawable.ic_w_warning, colors.error, (24f * dims.scale).dp)
    Spacer(GlanceModifier.height(6.dp))
    Text(context.getString(R.string.w_error_title), style = style(colors.text, dims.task, bold = true))
    state.snapshot.errorMessage?.let {
      Spacer(GlanceModifier.height(2.dp))
      Text(it, style = style(colors.muted, dims.meta, align = TextAlign.Center), maxLines = 3)
    }
    Spacer(GlanceModifier.height(4.dp))
    Text(
      when {
        state.ui.refreshing -> context.getString(R.string.w_refreshing)
        auth -> context.getString(R.string.w_error_fix_token)
        else -> context.getString(R.string.w_error_retry)
      },
      style = style(colors.accent, dims.meta, bold = true),
    )
  }
}

@Composable
private fun ColumnScope.Ready(state: WidgetState, config: WidgetConfig, colors: WidgetColors, dims: Dims, env: WidgetEnv) {
  val now = System.currentTimeMillis()
  val view = state.view(now)
  val barOpen = state.ui.filterBarOpen
  // The header band stays opaque (full width; the widget's rounded corners clip it); only the list below
  // takes the background opacity / image.
  Column(
    modifier = GlanceModifier.fillMaxWidth().background(colors.headerBackground)
      .padding(start = dims.pad, top = dims.pad, end = dims.pad),
  ) {
    Header(state, config, view, barOpen, colors, dims, env)
    Spacer(GlanceModifier.height(8.dp))
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.divider)) {}
  }
  Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(start = dims.pad, end = dims.pad, bottom = dims.pad)) {
    if (view.scopedTotal == 0 && !barOpen) {
      Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) { AllClear(colors, dims) }
    } else {
      TaskList(state, config, view, now, barOpen, colors, dims, env)
    }
  }
}

@Composable
private fun ColumnScope.TaskList(
  state: WidgetState,
  config: WidgetConfig,
  view: TaskListView,
  now: Long,
  barOpen: Boolean,
  colors: WidgetColors,
  dims: Dims,
  env: WidgetEnv,
) {
  val filterEmpty = view.total == 0 && view.scopedTotal > 0
  val context = LocalContext.current
  val inFlight = state.ui.pendingCompletions + state.committing
  val toastShowing = toastText(state, now, context) != null
  LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
    if (barOpen) filterBar(view, state.ui.refreshing, !headerLayout(config, dims).showRefresh, colors, dims, env, context)
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
  val hl = headerLayout(config, dims)
  Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    // The title area toggles the filter bar; the right-hand controls have their own taps.
    Column(modifier = GlanceModifier.defaultWeight().clickIf(env) { actionRunCallback<ToggleFilterBarAction>() }) {
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
            context.getString(R.string.w_offline).takeIf { state.snapshot.offline },
          ).joinToString(" • ")
        }
        if (sub.isNotEmpty()) Text(sub, style = style(colors.muted, dims.meta), maxLines = 1)
      }
    }
    // Narrow widgets drop the count / "tasks" word / Refresh so the title keeps its room.
    if (hl.showCount) {
      Spacer(GlanceModifier.width(8.dp))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(view.total.toString(), style = style(colors.accent, dims.count, bold = true), maxLines = 1)
        if (hl.showTasksWord) {
          Spacer(GlanceModifier.width(4.dp))
          Text(
            context.getString(if (view.total == 1) R.string.w_task_one else R.string.w_task_many),
            modifier = GlanceModifier.padding(bottom = (4f * dims.scale).dp),
            style = style(colors.muted, dims.meta),
          )
        }
      }
    }
    Spacer(GlanceModifier.width(4.dp))
    if (hl.showRefresh) {
      HeaderButton(
        R.drawable.ic_w_refresh, context.getString(R.string.w_cd_refresh),
        if (state.ui.refreshing) colors.muted else colors.accent, hl.buttonDp, dims, env,
        if (state.ui.refreshing) null else ({ actionRunCallback<RefreshAction>() }),
      )
    }
    HeaderButton(R.drawable.ic_w_add, context.getString(R.string.w_cd_new_task), colors.accent, hl.buttonDp, dims, env) {
      actionStartActivity(Nav.newTaskIntent(context, env.appWidgetId))
    }
  }
}

private fun headerLayout(config: WidgetConfig, dims: Dims) =
  WidgetMath.headerLayout(dims.contentWidth, dims.scale, config.showCount, dims.bp.short || dims.bp.narrow)

/** A square tap target ([sizeDp], 32 or 28) with a centered icon, for the header's Refresh and New-task buttons. */
@Composable
private fun HeaderButton(
  @DrawableRes icon: Int,
  description: String,
  tint: ColorProvider,
  sizeDp: Int,
  dims: Dims,
  env: WidgetEnv,
  action: (() -> Action)?,
) {
  Box(modifier = GlanceModifier.size(sizeDp.dp).let { if (action != null) it.clickIf(env, action) else it }, contentAlignment = Alignment.Center) {
    Image(
      provider = ImageProvider(icon),
      contentDescription = description,
      modifier = GlanceModifier.size((18f * dims.scale).dp),
      colorFilter = ColorFilter.tint(tint),
    )
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

private fun LazyListScope.filterBar(
  view: TaskListView,
  refreshing: Boolean,
  showRefreshPill: Boolean,
  colors: WidgetColors,
  dims: Dims,
  env: WidgetEnv,
  context: Context,
) {
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
  // Actions first: on small widgets the chip rows push anything below them out of view.
  item(WidgetMath.stableId("__actions")) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
      // The header drops its Refresh button when narrow; keep it reachable here.
      if (showRefreshPill) {
        ActionPill(
          R.drawable.ic_w_refresh, context.getString(R.string.w_refresh),
          colors, dims, env,
          if (refreshing) null else ({ actionRunCallback<RefreshAction>() }),
        )
      }
      ActionPill(R.drawable.ic_w_settings, context.getString(R.string.w_settings), colors, dims, env) {
        actionStartActivity(Nav.configIntent(context, env.appWidgetId))
      }
      // Both pills need ~210dp; below that "Open ClickUp" would only show truncated.
      if (dims.contentWidth >= 210f) {
        ActionPill(R.drawable.ic_w_open, context.getString(R.string.w_open_clickup), colors, dims, env) {
          actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://app.clickup.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
          )
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
  item(WidgetMath.stableId("__filterbar_end")) {
    Box(GlanceModifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
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
  action: (() -> Action)?,
) {
  Box(GlanceModifier.padding(end = 6.dp)) {
    Row(
      modifier = GlanceModifier
        .background(ImageProvider(R.drawable.widget_chip_off), colorFilter = ColorFilter.tint(colors.accent))
        .padding(horizontal = 8.dp, vertical = 4.dp)
        .let { if (action != null) it.clickIf(env, action) else it },
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
        val listName = task.listName.takeIf { dims.showListName && it.isNotBlank() }
        val requester = task.requester?.takeIf { dims.showRequester && it.isNotBlank() }
        val due = row.due.takeIf { config.showDueChip }
        val prio = task.priority.takeIf { config.showPriority && it != Priority.NONE }
        val tagLabels = task.tags.takeIf { config.showTags && !dims.bp.narrow }.orEmpty().map { WidgetMath.tagLabel(it) }
        val icon = dims.meta * 0.95f
        // Line 1: name, due right. Line 2: list name, requester, tags left; priority right (under the due date).
        val layout = WidgetMath.rowLayout(
          textWidthDp = dims.textWidth,
          listName = listName,
          requester = requester,
          priorityWidthDp = prio?.let { icon + if (dims.bp.narrow) 0f else 3f + dims.measure.widthDp(it.label, dims.meta, true) },
          tagLabels = tagLabels,
          chipSp = dims.chip,
          metaSp = dims.meta,
          requesterChromeDp = icon + 3f,
          measure = dims.measure,
        )
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
          Text(
            task.name,
            modifier = GlanceModifier.defaultWeight(),
            style = style(if (inFlight) colors.muted else colors.text, dims.task, strike = inFlight),
            maxLines = 2,
          )
          if (due != null) {
            Spacer(GlanceModifier.width(WidgetMath.PART_GAP_DP.dp))
            DuePart(due, colors, dims, modifier = GlanceModifier.padding(top = ((dims.task - dims.meta) / 2f).dp))
          }
        }
        val tagsMeta = layout.tags == WidgetMath.Slot.META_LINE
        if (listName != null || requester != null || prio != null || tagsMeta) {
          Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Weighted, so a width misestimate clips the left parts instead of pushing priority off the row.
            Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
              if (layout.listName != null) {
                Text(layout.listName, style = style(colors.muted, dims.meta), maxLines = 1)
              }
              if (layout.requester != null) {
                if (layout.listName != null) Spacer(GlanceModifier.width(WidgetMath.PART_GAP_DP.dp))
                RequesterPart(layout.requester, task.requesterColorHex, colors, dims)
              }
              if (tagsMeta) {
                if (listName != null || requester != null) Spacer(GlanceModifier.width(WidgetMath.PART_GAP_DP.dp))
                TagChips(task, tagLabels, layout.tagChips, colors, dims)
              }
            }
            if (prio != null) { Spacer(GlanceModifier.width(WidgetMath.PART_GAP_DP.dp)); PriorityPart(prio, colors, dims) }
          }
        }
        if (layout.tags == WidgetMath.Slot.BELOW && layout.tagChips > 0) {
          Box(GlanceModifier.padding(top = 2.dp)) { TagChips(task, tagLabels, layout.tagChips, colors, dims) }
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

/** Calendar icon + due label in its urgency color. */
@Composable
private fun DuePart(due: DueInfo, colors: WidgetColors, dims: Dims, modifier: GlanceModifier = GlanceModifier) {
  val color = when (due.cls) {
    DueClass.OVERDUE -> colors.overdue
    DueClass.TODAY -> colors.today
    DueClass.SOON -> colors.soon
    DueClass.LATER -> colors.muted
  }
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Icon(R.drawable.ic_w_calendar, color, (dims.meta * 0.95f).dp)
    Spacer(GlanceModifier.width(3.dp))
    Text(due.label, style = style(color, dims.meta, bold = true), maxLines = 1)
  }
}

/** Dot in the requester's drop-down color (as on the details sheet), then the name in muted text. */
@Composable
private fun RequesterPart(name: String, colorHex: String?, colors: WidgetColors, dims: Dims) {
  val dot = colorHex?.let { ColorProvider(Color(TaskLogic.parseColor(it))) } ?: colors.muted
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(R.drawable.ic_w_dot, dot, (dims.meta * 0.95f).dp)
    Spacer(GlanceModifier.width(3.dp))
    Text(name, style = style(colors.muted, dims.meta), maxLines = 1)
  }
}

/** Flag in the priority color, plus its name unless the widget is narrow. */
@Composable
private fun PriorityPart(priority: Priority, colors: WidgetColors, dims: Dims) {
  val color = colors.priority(priority)
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(R.drawable.ic_w_flag, color, (dims.meta * 0.95f).dp)
    if (!dims.bp.narrow) {
      Spacer(GlanceModifier.width(3.dp))
      Text(priority.label, style = style(color, dims.meta, bold = true), maxLines = 1)
    }
  }
}

/** The first [shown] tag chips (solid tag color) then "+N" for the rest. */
@Composable
private fun TagChips(task: TaskItem, labels: List<String>, shown: Int, colors: WidgetColors, dims: Dims) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    for (i in 0 until shown) {
      // Solid tag color (as ClickUp shows tags) with black/white text by luminance: a translucent tint
      // renders washed-out in RemoteViews, leaving light text on a pale chip.
      val fill = TaskLogic.parseColor(task.tagColors[task.tags[i]], fallback = 0xFF6B7280)
      val ink = ColorProvider(Color(if (Palettes.isDark(fill)) 0xFFFFFFFF else 0xFF111111))
      Box(modifier = GlanceModifier.padding(end = if (i < shown - 1 || shown < labels.size) 4.dp else 0.dp)) {
        Box(
          modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_pill), colorFilter = ColorFilter.tint(ColorProvider(Color(fill))))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
          Text(labels[i], style = style(ink, dims.chip, bold = true), maxLines = 1)
        }
      }
    }
    if (shown < labels.size) Text("+${labels.size - shown}", style = style(colors.muted, dims.chip, bold = true), maxLines = 1)
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

/** Decodes the configured background image once per path + mtime + opacity (≤ ~720 px long edge). */
private object BackgroundImages {
  private const val MAX_EDGE = 720
  private var key: String? = null
  private var cached: Bitmap? = null

  @Synchronized
  fun load(path: String, opacityPercent: Int): Bitmap? {
    val file = File(path)
    if (!file.isFile) return null
    val k = "$path@${file.lastModified()}@$opacityPercent"
    if (k == key) return cached
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val bitmap = runCatching {
      BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = WidgetMath.sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE) },
      )
    }.getOrNull()?.let { withOpacity(it, opacityPercent) }
    key = k
    cached = bitmap
    return bitmap
  }

  /** RemoteViews images have no alpha attribute, so the opacity is baked into a copy. */
  private fun withOpacity(src: Bitmap, opacityPercent: Int): Bitmap {
    val alpha = (opacityPercent.coerceIn(0, 100) * 255 + 50) / 100
    if (alpha >= 255) return src
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { this.alpha = alpha })
    src.recycle()
    return out
  }
}
