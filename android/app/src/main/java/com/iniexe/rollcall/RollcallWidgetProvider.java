package com.iniexe.rollcall;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

abstract class RollcallWidgetProvider extends AppWidgetProvider {
    abstract RollcallWidgets.Type widgetType();

    @Override public final void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        RollcallWidgets.update(context, manager, widgetIds, widgetType());
    }

    @Override public final void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
                                                           int widgetId, android.os.Bundle options) {
        RollcallWidgets.update(context, manager, new int[]{widgetId}, widgetType());
    }
}
