package com.iniexe.rollcall;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

final class RollCallAuthRepository {
    static final String PREFS = "rollcall_cloud_account";
    static final String EMAIL_LINK_EMAIL = "email_link_email";
    static final String EMAIL_LINK_UID = "email_link_uid";
    static final String EMAIL_LINK_MODE = "email_link_mode";
    static final String EMAIL_LINK_REAUTH = "email_link_reauth";

    private final FirebaseAuth auth;
    private final SharedPreferences preferences;

    RollCallAuthRepository(Context context) {
        auth = FirebaseAuth.getInstance();
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Task<Void> sendEmailLink(String email, boolean linking) {
        FirebaseUser current = auth.getCurrentUser();
        ActionCodeSettings settings = ActionCodeSettings.newBuilder()
                .setUrl("https://rollcall-ab5d1.firebaseapp.com")
                .setHandleCodeInApp(true)
                .setAndroidPackageName("com.iniexe.rollcall", true, null)
                .build();
        preferences.edit()
                .putString(EMAIL_LINK_EMAIL, email)
                .putBoolean(EMAIL_LINK_MODE, linking)
                .putBoolean(EMAIL_LINK_REAUTH, false)
                .putString(EMAIL_LINK_UID, linking && current != null ? current.getUid() : "")
                .apply();
        return auth.sendSignInLinkToEmail(email, settings);
    }

    Task<Void> sendEmailReauthenticationLink(String email) {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null || !email.equalsIgnoreCase(current.getEmail()))
            throw new IllegalStateException("Use the email address on your current Rollcall account.");
        ActionCodeSettings settings = ActionCodeSettings.newBuilder()
                .setUrl("https://rollcall-ab5d1.firebaseapp.com")
                .setHandleCodeInApp(true)
                .setAndroidPackageName("com.iniexe.rollcall", true, null)
                .build();
        preferences.edit().putString(EMAIL_LINK_EMAIL, email).putBoolean(EMAIL_LINK_MODE, false)
                .putBoolean(EMAIL_LINK_REAUTH, true).putString(EMAIL_LINK_UID, current.getUid()).apply();
        return auth.sendSignInLinkToEmail(email, settings);
    }

    Task<AuthResult> completeEmailLink(String email, String link, boolean linking, String expectedUid) {
        if (!auth.isSignInWithEmailLink(link)) throw new IllegalArgumentException("This is not a valid Rollcall sign-in link.");
        if (linking) {
            FirebaseUser current = auth.getCurrentUser();
            if (current == null || expectedUid == null || !expectedUid.equals(current.getUid()))
                throw new IllegalStateException("Sign in to the same Rollcall account that requested this link, then open the email again.");
            AuthCredential credential = EmailAuthProvider.getCredentialWithLink(email, link);
            return current.linkWithCredential(credential);
        }
        return auth.signInWithEmailLink(email, link);
    }

    Task<Void> reauthenticateWithEmailLink(String email, String link, String expectedUid) {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null || expectedUid == null || !expectedUid.equals(current.getUid()))
            throw new IllegalStateException("Sign in to the same Rollcall account that requested this link.");
        return current.reauthenticate(EmailAuthProvider.getCredentialWithLink(email, link));
    }

    Task<AuthResult> signInWithPassword(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password);
    }

    Task<AuthResult> createWithPassword(String email, String password) {
        return auth.createUserWithEmailAndPassword(email, password);
    }

    Task<Void> resetPassword(String email) {
        return auth.sendPasswordResetEmail(email);
    }

    Task<AuthResult> signInWithGoogle(String idToken) {
        return auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null));
    }

    Task<AuthResult> linkGoogle(String idToken) {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null) throw new IllegalStateException("Sign in to the Rollcall account you want to keep before connecting Google.");
        return current.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null));
    }

    Task<Void> reauthenticateWithGoogle(String idToken) {
        FirebaseUser current = auth.getCurrentUser();
        if (current == null) throw new IllegalStateException("Sign in again before continuing.");
        return current.reauthenticate(GoogleAuthProvider.getCredential(idToken, null));
    }

    static String userMessage(Exception error) {
        String lower = error instanceof FirebaseAuthException
                ? ((FirebaseAuthException) error).getErrorCode().toLowerCase(java.util.Locale.ROOT)
                : String.valueOf(error.getMessage()).toLowerCase(java.util.Locale.ROOT);
        lower = lower.replace('_', '-');
        if (lower.contains("account-exists-with-different-credential"))
            return "This email is already linked to another sign-in method. Sign in with that method first, then connect Google in account settings.";
        if (lower.contains("credential-already-in-use") || lower.contains("email-already-in-use"))
            return "That sign-in method belongs to another Rollcall account. No accounts were merged.";
        if (lower.contains("network-request-failed")) return "Connection lost. Check your internet and try again.";
        if (lower.contains("too-many-requests")) return "Too many attempts. Wait a little before trying again.";
        if (lower.contains("user-disabled")) return "This Rollcall account is disabled. Contact support for help.";
        if (lower.contains("expired-action-code") || lower.contains("invalid-action-code"))
            return "This sign-in link has expired or was already used. Request a fresh link.";
        if (lower.contains("invalid-email")) return "Enter a valid email address.";
        if (lower.contains("wrong-password") || lower.contains("invalid-credential"))
            return "The email or password could not be verified.";
        return "Rollcall could not complete sign-in. Please try again.";
    }
}
