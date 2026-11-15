import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import { getAuth, signInWithEmailAndPassword, signOut } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';

const config = window.ROLLCALL_FIREBASE_CONFIG || {};
const configured = config.apiKey && !config.apiKey.startsWith('REPLACE_') && config.appId && !config.appId.startsWith('REPLACE_');
const dialog = document.querySelector('#web-account-dialog');
const form = document.querySelector('#web-account-form');
const feedback = document.querySelector('#web-account-feedback');
const emailStage = document.querySelector('#web-email-stage');
const qrStage = document.querySelector('#web-qr-stage');
const qrCode = document.querySelector('#web-qr-code');
const qrFeedback = document.querySelector('#web-qr-feedback');
const qrCountdown = document.querySelector('#web-qr-countdown');
const qrRefresh = document.querySelector('#web-qr-refresh');
const emailMode = document.querySelector('#web-email-mode');
const qrMode = document.querySelector('#web-qr-mode');
const codeRow = document.querySelector('#web-code-row');
const resend = document.querySelector('#web-resend-code');
const submit = document.querySelector('#web-account-submit');
const accountButton = document.querySelector('#web-account-button');
let auth, stage = 'credentials', authMode = 'qr', challengeId = '';
let resendUntil = 0, resendTimer, qrPollTimer, qrClockTimer, qrExpiresAt = 0, qrActive = false, qrBusy = false;

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
function qrMessage(text, error = false) { qrFeedback.textContent = text; qrFeedback.classList.toggle('error', error); }
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
async function showSnapshot() {
  const result = await api('snapshot');
  window.dispatchEvent(new CustomEvent('rollcall-cloud-snapshot', { detail: result.snapshot }));
  api('announcements').then(inbox => {
    window.dispatchEvent(new CustomEvent('rollcall-cloud-announcements', { detail: inbox.announcements || [] }));
  }).catch(() => {
    window.dispatchEvent(new CustomEvent('rollcall-cloud-announcements', { detail: null }));
  });
  accountButton.textContent = 'Sign out';
  document.body.classList.add('remote-data');
  message('Connected · data is read-only and synced from your phone.');
  if (dialog.open) dialog.close();
  if (new URLSearchParams(location.search).get('admin') === '1') location.assign('/admin/');
}
function stopQrLogin() {
  qrActive = false;
  clearTimeout(qrPollTimer);
  clearInterval(qrClockTimer);
  qrBusy = false;
}
function updateQrClock() {
  const seconds = Math.max(0, Math.ceil((qrExpiresAt - Date.now()) / 1000));
  qrCountdown.textContent = seconds ? `Expires in ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` : 'Code expired';
  if (!seconds) {
    stopQrLogin();
    qrMessage('This code expired. Refresh it to try again.', true);
    qrRefresh.hidden = false;
  }
}
async function pollQrLogin(challenge) {
  if (!qrActive || qrBusy) return;
  qrBusy = true;
  try {
    const result = await api('auth/qr/poll', { method: 'POST', body: JSON.stringify(challenge) });
    if (result.state === 'signed_in') {
      stopQrLogin();
      accountButton.textContent = 'Sign out';
      await showSnapshot();
      return;
    }
    if (result.state === 'denied') {
      stopQrLogin();
      qrMessage('This request was declined. Refresh the code to start again.', true);
      qrRefresh.hidden = false;
      return;
    }
    qrMessage('Waiting for approval on your phone…');
  } catch (error) {
    if (error.status === 404 || error.status === 409) {
      stopQrLogin();
      qrMessage('This code expired or was already used. Refresh it to try again.', true);
      qrRefresh.hidden = false;
      return;
    }
    qrMessage(error.message || 'Could not check this sign-in request.', true);
  } finally {
    qrBusy = false;
    if (qrActive) qrPollTimer = setTimeout(() => pollQrLogin(challenge), 1800);
  }
}
async function beginQrLogin() {
  stopQrLogin();
  qrActive = true;
  qrRefresh.hidden = true;
  qrCode.replaceChildren();
  qrMessage('Preparing a secure sign-in code…');
  qrCountdown.textContent = 'Expires in 2:00';
  try {
    const challenge = await api('auth/qr/start', { method: 'POST', body: JSON.stringify({}) });
    if (!qrActive) return;
    if (!window.QRCode) throw new Error('QR rendering is unavailable. Use email sign-in instead.');
    new window.QRCode(qrCode, { text: challenge.scanPayload, width: 240, height: 240, colorDark: '#142017', colorLight: '#FFFFFF', correctLevel: window.QRCode.CorrectLevel.M });
    qrExpiresAt = challenge.expiresAt * 1000;
    qrMessage('Waiting for approval on your phone…');
    updateQrClock();
    qrClockTimer = setInterval(updateQrClock, 1000);
    qrPollTimer = setTimeout(() => pollQrLogin({ challengeId: challenge.challengeId, secret: challenge.secret }), 1200);
  } catch (error) {
    stopQrLogin();
    qrMessage(error.message || 'Could not create a sign-in code.', true);
    qrRefresh.hidden = false;
  }
}
function showEmailMode() {
  stopQrLogin();
  authMode = 'email';
  qrStage.hidden = true;
  emailStage.hidden = false;
  submit.hidden = false;
  form.elements.email.required = true;
  form.elements.password.required = true;
  if (!configured) message('Email sign-in needs the Firebase web app settings. Phone QR sign-in is still available.', true);
  else message('');
  form.elements.email.focus();
}
function showQrMode() {
  authMode = 'qr';
  stage = 'credentials';
  challengeId = '';
  resend.hidden = true;
  clearInterval(resendTimer);
  codeRow.hidden = true;
  form.elements.code.required = false;
  form.elements.email.required = false;
  form.elements.password.required = false;
  emailStage.hidden = true;
  qrStage.hidden = false;
  submit.hidden = true;
  message('');
  beginQrLogin();
}
function openAccountDialog() {
  stage = 'credentials';
  authMode = 'qr';
  dialog.showModal();
  showQrMode();
}

accountButton.addEventListener('click', async () => {
  if (document.body.classList.contains('remote-data')) {
    await api('auth/logout', { method: 'POST' }).catch(() => {});
    if (auth) await signOut(auth).catch(() => {});
    stopQrLogin();
    document.body.classList.remove('remote-data');
    accountButton.textContent = 'Sign in';
    window.dispatchEvent(new CustomEvent('rollcall-cloud-logout'));
    return;
  }
  openAccountDialog();
});

emailMode.addEventListener('click', showEmailMode);
qrMode.addEventListener('click', showQrMode);
qrRefresh.addEventListener('click', beginQrLogin);

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (authMode !== 'email' || !configured) return;
  submit.disabled = true;
  try {
    if (stage === 'credentials') {
      const credential = await signInWithEmailAndPassword(auth, form.elements.email.value.trim(), form.elements.password.value);
      if (!credential.user.emailVerified) throw new Error('Verify your email in the RollCall app before using web sign-in.');
      await requestCode(credential.user);
      stage = 'code'; codeRow.hidden = false; form.elements.code.required = true;
      resend.hidden = false;
      form.elements.password.closest('label').hidden = true;
      form.elements.password.required = false;
      message('Code sent to your trusted RollCall app. It expires in 5 minutes.');
      submit.textContent = 'Verify code';
    } else if (stage === 'code') {
      await api('auth/verify', { method: 'POST', body: JSON.stringify({ challengeId, code: form.elements.code.value.trim() }) });
      await signOut(auth);
      stage = 'snapshot';
      accountButton.textContent = 'Sign out';
      form.elements.email.closest('label').hidden = true;
      form.elements.password.closest('label').hidden = true;
      form.elements.email.required = false;
      form.elements.password.required = false;
      codeRow.hidden = true;
      message('Code approved. Loading your latest phone snapshot…');
      submit.textContent = 'Check snapshot';
      await showSnapshot();
    } else await showSnapshot();
  } catch (error) { message(error.message || 'Sign-in failed.', true); }
  finally { submit.disabled = false; }
});

dialog.addEventListener('close', () => {
  stopQrLogin();
  sessionStorage.setItem('rollcall-signin-dismissed', '1');
  clearInterval(resendTimer); resend.hidden = true; resendUntil = 0;
  form.reset(); stage = 'credentials'; authMode = 'qr'; challengeId = '';
  qrStage.hidden = false; emailStage.hidden = true; submit.hidden = true; submit.textContent = 'Sign in';
  form.elements.email.required = false; form.elements.password.required = false;
  form.elements.email.closest('label').hidden = false;
  form.elements.password.closest('label').hidden = false;
  form.elements.email.required = false;
  form.elements.password.required = false;
  form.elements.code.required = false; codeRow.hidden = true;
});

resend.addEventListener('click', async () => {
  if (stage !== 'code' || resend.disabled) return;
  resend.disabled = true;
  try {
    await requestCode(auth.currentUser);
    form.elements.code.value = '';
    message('A new code was sent to your trusted RollCall app. Use the latest code.');
  } catch (error) { message(error.message || 'Could not resend the code.', true); updateResend(); }
});

if (configured) auth = getAuth(initializeApp(config));
api('auth/session').then(result => {
  if (result.signedIn) { accountButton.textContent = 'Sign out'; return showSnapshot(); }
}).catch(() => {
  if (!localStorage.getItem('rollcall.web.v1') && !sessionStorage.getItem('rollcall-signin-dismissed')) openAccountDialog();
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
