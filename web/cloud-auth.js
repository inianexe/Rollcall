import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import { getAuth, signInWithEmailAndPassword, signOut } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';

const config = window.ROLLCALL_FIREBASE_CONFIG || {};
const configured = config.apiKey && !config.apiKey.startsWith('REPLACE_') && config.appId && !config.appId.startsWith('REPLACE_');
const dialog = document.querySelector('#web-account-dialog');
const form = document.querySelector('#web-account-form');
const feedback = document.querySelector('#web-account-feedback');
const codeRow = document.querySelector('#web-code-row');
const resend = document.querySelector('#web-resend-code');
const submit = document.querySelector('#web-account-submit');
const accountButton = document.querySelector('#web-account-button');
let auth, stage = 'credentials', challengeId = '';
let resendUntil = 0, resendTimer;

function updateResend() {
  const seconds = Math.max(0, Math.ceil((resendUntil - Date.now()) / 1000));
  resend.disabled = seconds > 0;
  resend.textContent = seconds ? `Resend code in ${seconds}s` : 'Resend code';
  if (!seconds) clearInterval(resendTimer);
}
async function requestCode(user) {
  const token = await user.getIdToken(true);
  const result = await api('auth/challenge', { method: 'POST', body: JSON.stringify({}), headers: { authorization: `Bearer ${token}` } });
  challengeId = result.challengeId;
  resendUntil = Date.now() + 30_000;
  clearInterval(resendTimer);
  resendTimer = setInterval(updateResend, 1000);
  updateResend();
}

function message(text, error = false) { feedback.textContent = text; feedback.classList.toggle('error', error); }
async function api(path, options = {}) {
  const response = await fetch(`/api/${path}`, { credentials: 'same-origin', cache: 'no-store', ...options,
    headers: { 'content-type': 'application/json', ...(options.headers || {}) } });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.error || 'The request could not be completed.');
  return body;
}
async function showSnapshot() {
  const result = await api('snapshot');
  window.dispatchEvent(new CustomEvent('rollcall-cloud-snapshot', { detail: result.snapshot }));
  accountButton.textContent = 'Sign out';
  document.body.classList.add('remote-data');
  message('Connected · data is read-only and synced from your phone.');
  if (dialog.open) dialog.close();
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
  stage = 'credentials'; challengeId = '';
  resend.hidden = true;
  codeRow.hidden = true; form.elements.code.required = false;
  message(configured ? '' : 'Web sign-in needs the Firebase web app settings to be added before deployment.', !configured);
  submit.disabled = !configured;
  dialog.showModal();
  form.elements.email.focus();
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (!configured) return;
  submit.disabled = true;
  try {
    if (stage === 'credentials') {
      const credential = await signInWithEmailAndPassword(auth, form.elements.email.value.trim(), form.elements.password.value);
      if (!credential.user.emailVerified) throw new Error('Verify your email in the Rollcall app before using web sign-in.');
      await requestCode(credential.user);
      stage = 'code'; codeRow.hidden = false; form.elements.code.required = true;
      resend.hidden = false;
      form.elements.password.closest('label').hidden = true;
      message('Code sent to your trusted Rollcall app. It expires in 5 minutes.');
      submit.textContent = 'Verify code';
    } else if (stage === 'code') {
      await api('auth/verify', { method: 'POST', body: JSON.stringify({ challengeId, code: form.elements.code.value.trim() }) });
      await signOut(auth);
      stage = 'snapshot';
      accountButton.textContent = 'Sign out';
      form.elements.email.closest('label').hidden = true;
      form.elements.password.closest('label').hidden = true;
      codeRow.hidden = true;
      message('Code approved. Loading the latest snapshot from your phone…');
      submit.textContent = 'Check snapshot';
      await showSnapshot();
    } else await showSnapshot();
  } catch (error) { message(error.message || 'Sign-in failed.', true); }
  finally { submit.disabled = false; }
});

dialog.addEventListener('close', () => {
  sessionStorage.setItem('rollcall-signin-dismissed', '1');
  clearInterval(resendTimer); resend.hidden = true; resendUntil = 0;
  form.reset(); stage = 'credentials'; challengeId = ''; codeRow.hidden = true;
  form.elements.email.closest('label').hidden = false;
  form.elements.password.closest('label').hidden = false;
  form.elements.code.required = false; submit.textContent = 'Sign in';
});

resend.addEventListener('click', async () => {
  if (stage !== 'code' || resend.disabled) return;
  resend.disabled = true;
  try {
    await requestCode(auth.currentUser);
    form.elements.code.value = '';
    message('A new code was sent to your trusted Rollcall app. Use the latest code.');
  } catch (error) { message(error.message || 'Could not resend the code.', true); updateResend(); }
});

if (configured) {
  auth = getAuth(initializeApp(config));
  api('auth/session').then(result => { if (result.signedIn) { accountButton.textContent = 'Sign out'; return showSnapshot(); } }).catch(() => {
    if (!localStorage.getItem('rollcall.web.v1') && !sessionStorage.getItem('rollcall-signin-dismissed')) { dialog.showModal(); form.elements.email.focus(); }
  });
  setInterval(() => { if (document.body.classList.contains('remote-data')) showSnapshot().catch(error => message(error.message, true)); }, 60_000);
  window.addEventListener('focus', () => { if (document.body.classList.contains('remote-data')) showSnapshot().catch(() => {}); });
}
