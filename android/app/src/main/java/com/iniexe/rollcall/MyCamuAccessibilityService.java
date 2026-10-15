package com.iniexe.rollcall;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public class MyCamuAccessibilityService extends AccessibilityService {
    private static MyCamuAccessibilityService instance;
    @Override public void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }

    static List<Attendance> readAttendance() {
        List<Attendance> rows = new ArrayList<>();
        if (instance == null) return rows;
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (isMyCamu(root)) collect(root, rows, 0);
        return rows;
    }
    static String readVisibleText() {
        if(instance==null)return "";
        AccessibilityNodeInfo root=instance.getRootInActiveWindow();
        if(!isMyCamu(root))return "";
        StringBuilder text=new StringBuilder(); collectText(root,text,0); return text.toString();
    }
    private static void collectText(AccessibilityNodeInfo node,StringBuilder out,int depth) {
        if(node==null||depth>50)return;
        if(node.getChildCount()==0) {
            CharSequence value=node.getText(); if(value==null)value=node.getContentDescription();
            if(value!=null&&!value.toString().trim().isEmpty())out.append(value).append('\n');
            return;
        }
        for(int i=0;i<node.getChildCount();i++)collectText(node.getChild(i),out,depth+1);
    }
    private static void collect(AccessibilityNodeInfo node, List<Attendance> rows, int depth) {
        if (node == null || depth > 50 || rows.size() >= 200) return;
        CharSequence text = node.getText(); if(text==null)text=node.getContentDescription();
        if (text != null) {
            Attendance row = Attendance.parse(text.toString());
            if (row != null) {
                for (Attendance found : rows) if (found.code.equals(row.code)) return;
                rows.add(row);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), rows, depth + 1);
    }
    private static boolean isMyCamu(AccessibilityNodeInfo root) {
        if(root==null||root.getPackageName()==null)return false;
        String packageName=root.getPackageName().toString();
        return packageName.equals("com.octoze.mycamu_web_lite")||packageName.equals("com.octoze.camu.mycamuapp");
    }
}
