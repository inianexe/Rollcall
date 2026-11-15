package com.iniexe.rollcall;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

final class SyncPathView extends SignalPanel {
    static final class Source {
        final String name, status, detail, recovery;
        final boolean running;
        Source(String name,String status,String detail,String recovery,boolean running) {
            this.name=name; this.status=status; this.detail=detail; this.recovery=recovery; this.running=running;
        }
    }
    private final Source[] sources;
    private final Runnable[] actions;
    SyncPathView(Context context, AppPalette palette, Typeface font, Source[] sources, Runnable[] actions) {
        super(context,palette,font); this.sources=sources.clone(); this.actions=actions.clone();
        setPadding(dp(16),dp(12),dp(16),dp(12));
        addView(copy("Sources → On this device → Cloud",14,palette.ink));
        addSpace(copy("Each source has its own update time. Cloud is a read-only web snapshot.",12,palette.muted),8);
        for(int i=0;i<sources.length;i++) {
            final int index=i; Source source=sources[i];
            TextView node=action(source.name+"\n"+source.status,()->open(index));
            addSpace(node,12);
            SignalPulseView path=new SignalPulseView(context,palette.muted,palette.highlight);
            addView(path,new LayoutParams(-1,dp(24))); path.setActive(source.running);
        }
    }
    private void open(int index) {
        Source source=sources[index];
        new AlertDialog.Builder(getContext()).setTitle(source.name).setMessage(source.status+"\n\n"+source.detail)
                .setPositiveButton(source.recovery,(dialog,which)->actions[index].run()).setNegativeButton("Close",null).show();
    }
}
