import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import {
  EmailAuthProvider, GoogleAuthProvider, getAuth, isSignInWithEmailLink, linkWithCredential,
  linkWithPopup, sendPasswordResetEmail, sendSignInLinkToEmail, signInWithEmailAndPassword,
  signInWithEmailLink, signInWithPopup, signOut
} from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';

const config = window.ROLLCALL_FIREBASE_CONFIG || {};
const configured = config.apiKey && !config.apiKey.startsWith('REPLACE_') && config.appId && !config.appId.startsWith('REPLACE_');
const filePreview = window.location.protocol === 'file:';
const dialog = document.querySelector('#web-account-dialog');
const form = document.querySelector('#web-account-form');
const feedback = document.querySelector('#web-account-feedback');
const passwordRow = document.querySelector('#web-password-row');
const passwordToggle = document.querySelector('#web-password-toggle');
const resetPassword = document.querySelector('#web-reset-password');
const linkGoogle = document.querySelector('#web-link-google');
const codeRow = document.querySelector('#web-code-row');
const approvalRow = document.querySelector('#web-approval-row');
const approvalNumber = document.querySelector('#web-approval-number');
const approvalStatus = document.querySelector('#web-approval-status');
const useCode = document.querySelector('#web-use-code');
const resend = document.querySelector('#web-resend-code');
const submit = document.querySelector('#web-account-submit');
const googleButton = document.querySelector('#web-google-signin');
const emailButton = document.querySelector('#web-email-link');
const accountButton = document.querySelector('#web-account-button');
let auth, stage = 'choose', challengeId = '';
let resendUntil = 0, resendTimer, approvalPollTimer, approvalPollBusy = false;

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
  if (code.includes('unauthorized-domain')) return 'This website address is not authorized for Rollcall sign-in. Open the official Rollcall site over HTTPS.';
  if (code.includes('operation-not-allowed')) return 'This sign-in option is not enabled for the Rollcall account yet.';
  if (code.includes('invalid-api-key')) return 'Rollcall web sign-in is misconfigured. Please try again later.';
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
  stage = 'approval';
  approvalRow.hidden = false;
  approvalNumber.textContent = result.matchCode || '··';
  approvalStatus.textContent = 'Waiting for approval on your phone…';
  codeRow.hidden = true;
  form.elements.code.required = false;
  resend.hidden = false;
  passwordRow.hidden = true;
  resetPassword.hidden = true;
  passwordToggle.hidden = true;
  googleButton.hidden = true;
  emailButton.hidden = true;
  submit.hidden = true;
  resendUntil = Date.now() + 30_000;
  clearInterval(resendTimer);
  resendTimer = setInterval(updateResend, 1000);
  updateResend();
  message('Open Rollcall on an enrolled phone. Compare the two-digit number and approve this browser request.');
  pollApprovalStatus();
  clearInterval(approvalPollTimer);
  approvalPollTimer = setInterval(pollApprovalStatus, 2200);
}

async function pollApprovalStatus() {
  if (stage !== 'approval' || !challengeId || approvalPollBusy) return;
  approvalPollBusy = true;
  try {
    const result = await api('auth/status', { method: 'POST', body: JSON.stringify({ challengeId }) });
    if (result.status === 'approved') {
      clearInterval(approvalPollTimer); approvalPollTimer = undefined;
      await signOut(auth);
      stage = 'snapshot';
      await showSnapshot();
    } else if (result.status === 'denied') {
      clearInterval(approvalPollTimer); approvalPollTimer = undefined;
      approvalStatus.textContent = 'The request was denied. If this was not you, change your password.';
      message('This browser sign-in was denied.', true);
    }
  } catch (error) {
    if (error.status === 404 || error.status === 409) {
      clearInterval(approvalPollTimer); approvalPollTimer = undefined;
      approvalStatus.textContent = error.message;
      message(error.message, true);
    }
  } finally { approvalPollBusy = false; }
}

function useVerificationCode() {
  if (stage !== 'approval') return;
  clearInterval(approvalPollTimer); approvalPollTimer = undefined;
  stage = 'code';
  approvalRow.hidden = true;
  codeRow.hidden = false;
  form.elements.code.required = true;
  submit.hidden = false;
  submit.textContent = 'Verify code';
  form.elements.code.focus();
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
  clearInterval(approvalPollTimer); approvalPollTimer = undefined;
  resend.hidden = true; codeRow.hidden = true; approvalRow.hidden = true; passwordRow.hidden = true;
  resetPassword.hidden = true; linkGoogle.hidden = !auth?.currentUser;
  passwordToggle.hidden = false; googleButton.hidden = false; emailButton.hidden = false;
  submit.hidden = true; form.elements.code.required = false;
  form.elements.email.required = true;
  form.elements.email.readOnly = false;
  form.elements.password.required = false;
  message(filePreview ? 'Sign-in is unavailable in a local file preview. Open Rollcall at https://rollcall-campus-companion.pages.dev.'
    : configured ? '' : 'Web sign-in needs Firebase web-app settings before it can be enabled.', filePreview || !configured);
  googleButton.disabled = !configured || filePreview;
  emailButton.disabled = !configured || filePreview;
  passwordToggle.disabled = !configured || filePreview;
  dialog.showModal();
  form.elements.email.focus();
}

useCode.addEventListener('click', useVerificationCode);

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
  clearInterval(resendTimer); clearInterval(approvalPollTimer); approvalPollTimer = undefined; resend.hidden = true; resendUntil = 0;
  form.reset(); stage = 'choose'; challengeId = ''; codeRow.hidden = true; approvalRow.hidden = true;
  passwordRow.hidden = true; resetPassword.hidden = true; submit.hidden = true;
  form.elements.password.required = false; form.elements.code.required = false;
});

resend.addEventListener('click', async () => {
  if (!['approval', 'code'].includes(stage) || resend.disabled) return;
  resend.disabled = true;
  try {
    await requestCode(auth.currentUser);
    form.elements.code.value = '';
    message('A new approval request was sent to your enrolled Rollcall phone.');
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
