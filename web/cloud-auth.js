import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import {
  EmailAuthProvider, GoogleAuthProvider, getAuth, isSignInWithEmailLink, linkWithCredential,
  linkWithPopup, sendPasswordResetEmail, sendSignInLinkToEmail, signInWithEmailAndPassword,
  signInWithEmailLink, signInWithPopup, signOut
} from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';

const config = window.ROLLCALL_FIREBASE_CONFIG || {};
const configured = config.apiKey && !config.apiKey.startsWith('REPLACE_') && config.appId && !config.appId.startsWith('REPLACE_');
const dialog = document.querySelector('#web-account-dialog');
const form = document.querySelector('#web-account-form');
const feedback = document.querySelector('#web-account-feedback');
const passwordRow = document.querySelector('#web-password-row');
const passwordToggle = document.querySelector('#web-password-toggle');
const resetPassword = document.querySelector('#web-reset-password');
const linkGoogle = document.querySelector('#web-link-google');
const codeRow = document.querySelector('#web-code-row');
const resend = document.querySelector('#web-resend-code');
const submit = document.querySelector('#web-account-submit');
const googleButton = document.querySelector('#web-google-signin');
const emailButton = document.querySelector('#web-email-link');
const accountButton = document.querySelector('#web-account-button');
let auth, stage = 'choose', challengeId = '';
let resendUntil = 0, resendTimer;

function updateResend() {
  const seconds = Math.max(0, Math.ceil((resendUntil - Date.now()) / 1000));
  resend.disabled = seconds > 0;
  resend.textContent = seconds ? `Resend code in ${seconds}s` : 'Resend code';
  if (!seconds) clearInterval(resendTimer);
}

function message(text, error = false) {
  feedback.textContent = text;
  feedback.classList.toggle('error', error);
}

function authMessage(error) {
  const code = String(error?.code || '').toLowerCase();
  if (code.includes('account-exists-with-different-credential'))
    return 'This email already uses another sign-in method. Sign in with that method first, then connect Google in Rollcall account settings.';
  if (code.includes('credential-already-in-use') || code.includes('email-already-in-use'))
    return 'That sign-in method belongs to another Rollcall account. No accounts were merged.';
  if (code.includes('network-request-failed')) return 'Connection lost. Check your internet and try again.';
  if (code.includes('too-many-requests')) return 'Too many attempts. Wait a little before trying again.';
  if (code.includes('user-disabled')) return 'This Rollcall account is disabled. Contact support for help.';
  if (code.includes('expired-action-code') || code.includes('invalid-action-code'))
    return 'This sign-in link expired or was already used. Request a fresh link.';
  if (code.includes('wrong-password') || code.includes('invalid-credential') || code.includes('user-not-found'))
    return 'The email or password could not be verified.';
  if (code.includes('popup-closed-by-user') || code.includes('cancelled-popup-request')) return 'Google sign-in was cancelled.';
  return 'Rollcall could not complete sign-in. Please try again.';
}

async function api(path, options = {}) {
  const response = await fetch(`/api/${path}`, { credentials: 'same-origin', cache: 'no-store', ...options,
    headers: { 'content-type': 'application/json', ...(options.headers || {}) } });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(body.error || 'The request could not be completed.');
    error.status = response.status;
    throw error;
  }
  return body;
}

async function requestCode(user) {
  if (!user.emailVerified) throw new Error('Verify your email before viewing synced Rollcall data.');
  const token = await user.getIdToken(true);
  const result = await api('auth/challenge', { method: 'POST', body: JSON.stringify({}), headers: { authorization: `Bearer ${token}` } });
  challengeId = result.challengeId;
  stage = 'code';
  codeRow.hidden = false;
  form.elements.code.required = true;
  resend.hidden = false;
  passwordRow.hidden = true;
  resetPassword.hidden = true;
  passwordToggle.hidden = true;
  googleButton.hidden = true;
  emailButton.hidden = true;
  submit.hidden = false;
  submit.textContent = 'Verify code';
  resendUntil = Date.now() + 30_000;
  clearInterval(resendTimer);
  resendTimer = setInterval(updateResend, 1000);
  updateResend();
  message('Approve this sign-in with the code in your trusted Rollcall app. It expires in 5 minutes.');
}

async function continueWithFirebaseUser(user) {
  try { await requestCode(user); }
  catch (error) { message(error.message || authMessage(error), true); }
}

async function showSnapshot() {
  const result = await api('snapshot');
  window.dispatchEvent(new CustomEvent('rollcall-cloud-snapshot', { detail: result.snapshot }));
  accountButton.textContent = 'Sign out';
  document.body.classList.add('remote-data');
  message('Connected · read-only academic data is synced from your phone.');
  if (dialog.open) dialog.close();
}

function prepareDialog() {
  stage = 'choose'; challengeId = '';
  resend.hidden = true; codeRow.hidden = true; passwordRow.hidden = true;
  resetPassword.hidden = true; linkGoogle.hidden = !auth?.currentUser;
  passwordToggle.hidden = false; googleButton.hidden = false; emailButton.hidden = false;
  submit.hidden = true; form.elements.code.required = false;
  form.elements.email.required = true;
  form.elements.email.readOnly = false;
  form.elements.password.required = false;
  message(configured ? '' : 'Web sign-in needs Firebase web-app settings before it can be enabled.', !configured);
  googleButton.disabled = !configured;
  emailButton.disabled = !configured;
  passwordToggle.disabled = !configured;
  dialog.showModal();
  form.elements.email.focus();
}

accountButton.addEventListener('click', async () => {
  if (document.body.classList.contains('remote-data')) {
    await api('auth/logout', { method: 'POST' }).catch(() => {});
    if (auth) await signOut(auth).catch(() => {});
    document.body.classList.remove('remote-data');
    accountButton.textContent = 'Sign in';
    window.dispatchEvent(new CustomEvent('rollcall-cloud-logout'));
    return;
  }
  prepareDialog();
});

passwordToggle.addEventListener('click', () => {
  stage = 'password';
  passwordRow.hidden = false;
  resetPassword.hidden = false;
  googleButton.hidden = true;
  emailButton.hidden = true;
  passwordToggle.hidden = true;
  linkGoogle.hidden = !auth.currentUser;
  submit.hidden = false;
  submit.textContent = 'Sign in';
  form.elements.password.required = true;
  form.elements.password.focus();
});

googleButton.addEventListener('click', async () => {
  if (!configured) return;
  googleButton.disabled = true;
  try {
    const result = await signInWithPopup(auth, new GoogleAuthProvider());
    await continueWithFirebaseUser(result.user);
  } catch (error) { message(authMessage(error), true); }
  finally { googleButton.disabled = false; }
});

emailButton.addEventListener('click', async () => {
  if (!configured) return;
  const email = form.elements.email.value.trim();
  if (!email) { form.elements.email.focus(); form.elements.email.reportValidity(); return; }
  emailButton.disabled = true;
  try {
    const current = auth.currentUser;
    const isLinking = Boolean(current);
    const settings = { url: window.location.origin, handleCodeInApp: true };
    await sendSignInLinkToEmail(auth, email, settings);
    localStorage.setItem('rollcall.email-link', email);
    if (isLinking) {
      sessionStorage.setItem('rollcall.email-link-uid', current.uid);
    }
    message('If this address can sign in, Firebase has emailed a one-time link. Open it on this device to continue.');
  } catch (error) { message(authMessage(error), true); }
  finally { emailButton.disabled = false; }
});

linkGoogle.addEventListener('click', async () => {
  const current = auth.currentUser;
  if (!current) return;
  linkGoogle.disabled = true;
  try {
    const linked = await linkWithPopup(current, new GoogleAuthProvider());
    if (linked.user.uid !== current.uid) throw new Error('The account identity changed unexpectedly. No account was merged.');
    message('Google is connected to your existing Rollcall account.');
  } catch (error) { message(authMessage(error), true); }
  finally { linkGoogle.disabled = false; }
});

resetPassword.addEventListener('click', async () => {
  const email = form.elements.email.value.trim();
  if (!email) { form.elements.email.focus(); form.elements.email.reportValidity(); return; }
  resetPassword.disabled = true;
  try {
    await sendPasswordResetEmail(auth, email);
    message('If a Rollcall account can receive a reset, Firebase has emailed instructions.');
  } catch (error) { message(authMessage(error), true); }
  finally { resetPassword.disabled = false; }
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  submit.disabled = true;
  try {
    if (stage === 'password') {
      const result = await signInWithEmailAndPassword(auth, form.elements.email.value.trim(), form.elements.password.value);
      await continueWithFirebaseUser(result.user);
    } else if (stage === 'email-completion') {
      const email = localStorage.getItem('rollcall.email-link') || form.elements.email.value.trim();
      if (!email) { form.elements.email.setCustomValidity('Enter the email that received the link.'); form.elements.email.reportValidity(); return; }
      const pendingUid = sessionStorage.getItem('rollcall.email-link-uid');
      let result;
      if (pendingUid) {
        const current = auth.currentUser;
        if (!current || current.uid !== pendingUid) throw new Error('Open this link on the device and Rollcall account that requested it.');
        result = await linkWithCredential(current, EmailAuthProvider.credentialWithLink(email, window.location.href));
      } else result = await signInWithEmailLink(auth, email, window.location.href);
      localStorage.removeItem('rollcall.email-link');
      sessionStorage.removeItem('rollcall.email-link-uid');
      window.history.replaceState({}, document.title, window.location.pathname + window.location.hash);
      await continueWithFirebaseUser(result.user);
    } else if (stage === 'code') {
      await api('auth/verify', { method: 'POST', body: JSON.stringify({ challengeId, code: form.elements.code.value.trim() }) });
      await signOut(auth);
      stage = 'snapshot';
      await showSnapshot();
    } else if (stage === 'snapshot') await showSnapshot();
  } catch (error) { message(error.message || authMessage(error), true); }
  finally { submit.disabled = false; }
});

dialog.addEventListener('close', () => {
  sessionStorage.setItem('rollcall-signin-dismissed', '1');
  clearInterval(resendTimer); resend.hidden = true; resendUntil = 0;
  form.reset(); stage = 'choose'; challengeId = ''; codeRow.hidden = true;
  passwordRow.hidden = true; resetPassword.hidden = true; submit.hidden = true;
  form.elements.password.required = false; form.elements.code.required = false;
});

resend.addEventListener('click', async () => {
  if (stage !== 'code' || resend.disabled) return;
  resend.disabled = true;
  try {
    await requestCode(auth.currentUser);
    form.elements.code.value = '';
    message('A new approval request was sent to your trusted Rollcall app.');
  } catch (error) { message(error.message || 'Could not send a new approval request.', true); updateResend(); }
});

if (configured) {
  auth = getAuth(initializeApp(config));
  if (isSignInWithEmailLink(auth, window.location.href)) {
    prepareDialog();
    stage = 'email-completion';
    const email = localStorage.getItem('rollcall.email-link');
    if (email) form.elements.email.value = email;
    form.elements.email.required = !email;
    form.elements.email.readOnly = Boolean(email);
    submit.hidden = false;
    submit.textContent = 'Continue';
    googleButton.hidden = true; emailButton.hidden = true; passwordToggle.hidden = true;
    message(email ? 'Confirm your email to finish signing in.' : 'This link opened on another device. Enter the email address that received it.');
  }
  api('auth/session').then(result => { if (result.signedIn) { accountButton.textContent = 'Sign out'; return showSnapshot(); } }).catch(() => {
    if (!localStorage.getItem('rollcall.web.v1') && !sessionStorage.getItem('rollcall-signin-dismissed')) prepareDialog();
  });
  const refreshSnapshot = () => showSnapshot().catch(error => {
    if (error.status === 401) {
      document.body.classList.remove('remote-data');
      accountButton.textContent = 'Sign in';
      window.dispatchEvent(new CustomEvent('rollcall-cloud-logout'));
      message('Your web session expired. Sign in again to view synced data.', true);
    } else message(error.message, true);
  });
  setInterval(() => { if (document.body.classList.contains('remote-data')) refreshSnapshot(); }, 60_000);
  window.addEventListener('focus', () => { if (document.body.classList.contains('remote-data')) refreshSnapshot(); });
} else {
  for (const button of [googleButton, emailButton, passwordToggle]) button.disabled = true;
}
