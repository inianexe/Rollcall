package com.iniexe.rollcall;

import android.content.Context;

public final class RollcallTodayWidget extends RollcallWidgetProvider {
    @Override RollcallWidgets.Type widgetType() { return RollcallWidgets.Type.NEXT; }

    static void refresh(Context context) { RollcallWidgets.refresh(context); }
}
