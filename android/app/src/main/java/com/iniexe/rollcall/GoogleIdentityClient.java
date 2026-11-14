package com.iniexe.rollcall;

import android.app.Activity;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.concurrent.Executor;

final class GoogleIdentityClient {
    interface Callback { void onToken(String token); void onError(String message); }
    private final Activity activity;
    private final CredentialManager manager;
    private final Executor executor = command -> new Handler(Looper.getMainLooper()).post(command);

    GoogleIdentityClient(Activity activity) {
        this.activity = activity;
        manager = CredentialManager.create(activity.getApplicationContext());
    }

    void requestIdToken(Callback callback) {
        int resource = activity.getResources().getIdentifier("default_web_client_id", "string", activity.getPackageName());
        if (resource == 0 || activity.getString(resource).trim().isEmpty()) {
            callback.onError("Google sign-in is not configured for this build yet. Update the Firebase Android configuration first.");
            return;
        }
        GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(resource))
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();
        manager.getCredentialAsync(activity, request, new CancellationSignal(), executor,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(@NonNull GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (!(credential instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            callback.onError("Google did not return a supported sign-in credential. Try again or use email.");
                            return;
                        }
                        try {
                            callback.onToken(GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData()).getIdToken());
                        } catch (Exception error) {
                            callback.onError("Google could not verify that account. Try again or use email.");
                        }
                    }
                    @Override public void onError(@NonNull GetCredentialException error) {
                        String name = error.getClass().getSimpleName();
                        if (name.contains("NoCredential")) callback.onError("No Google account is available on this device. Add one in Android settings or use email.");
                        else if (name.contains("GetCredentialCancellation")) callback.onError("Google sign-in was cancelled.");
                        else callback.onError("Google sign-in is temporarily unavailable. Check Google Play services and your connection.");
                    }
                });
    }
}
