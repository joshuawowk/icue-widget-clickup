package net.jwowk.clickupwidget

import android.app.Application
import net.jwowk.clickupwidget.widget.WidgetUpdater

class ClickUpWidgetApp : Application() {
  override fun onCreate() {
    super.onCreate()
    Graph.init(this)
    WidgetUpdater.onAppStart(this)
  }
}
