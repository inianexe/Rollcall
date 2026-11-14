package com.iniexe.rollcall;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public final class CloudSnapshotSyncWorker extends Worker {
    public CloudSnapshotSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }

    @NonNull @Override public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified() || !CloudSyncPolicy.allowsUpload(user.getUid(),
                getApplicationContext().getSharedPreferences("rollcall_cloud_account", Context.MODE_PRIVATE)
                        .getString("cloud_sync_uid", ""))) return Result.success();
        try {
            String deviceId = CloudAccountClient.deviceId(getApplicationContext());
            String token = Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).getToken();
            FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
            String syncOwner = getApplicationContext().getSharedPreferences("rollcall_cloud_account", Context.MODE_PRIVATE)
                    .getString("cloud_sync_uid", "");
            if (current == null || !user.getUid().equals(current.getUid())
                    || !CloudSyncPolicy.allowsUpload(user.getUid(), syncOwner)) return Result.success();
            JSONObject payload = CloudSnapshotPayload.build(getApplicationContext());
            CloudAccountClient.putSnapshot(token, deviceId, payload);
            getApplicationContext().getSharedPreferences("rollcall_cloud_account", Context.MODE_PRIVATE).edit()
                    .putLong("cloud_last_sync_at", System.currentTimeMillis())
                    .putString("cloud_last_sync_uid", user.getUid()).apply();
            return Result.success();
        } catch (Exception error) {
            return getRunAttemptCount() < 8 ? Result.retry() : Result.failure();
        }
    }
}
