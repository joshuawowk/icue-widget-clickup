package net.jwowk.clickupwidget

import android.content.Context
import net.jwowk.clickupwidget.data.ClickUpTaskRepository
import net.jwowk.clickupwidget.data.ConfigStore
import net.jwowk.clickupwidget.data.DemoTaskRepository
import net.jwowk.clickupwidget.data.TaskRepository
import net.jwowk.clickupwidget.data.TokenStore

/** Minimal service locator. Initialised in [ClickUpWidgetApp.onCreate]; safe to use from workers/receivers. */
object Graph {
  private lateinit var appContext: Context

  fun init(context: Context) {
    appContext = context.applicationContext
  }

  val tokenStore: TokenStore by lazy { TokenStore(appContext) }
  val configStore: ConfigStore by lazy { ConfigStore(appContext) }

  private val clickUp: TaskRepository by lazy { ClickUpTaskRepository(appContext, tokenStore) }
  private val demo: TaskRepository by lazy { DemoTaskRepository() }

  /** Sample-data repository when demo mode is on, otherwise the live ClickUp one. Re-check per use. */
  fun repository(): TaskRepository = if (configStore.appSettings().demoMode) demo else clickUp
}
