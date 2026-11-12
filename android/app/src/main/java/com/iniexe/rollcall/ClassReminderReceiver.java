package com.iniexe.rollcall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ClassReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || ClassReminderScheduler.ACTION_RESCHEDULE.equals(action)) {
            ClassReminderScheduler.scheduleSaved(context);
        } else if (ClassReminderScheduler.ACTION_REMINDER.equals(action)) {
            ClassReminderScheduler.showReminder(context, intent);
        }
    }
}
