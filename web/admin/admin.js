import { initializeApp } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-app.js';
import { getAuth, GoogleAuthProvider, signInWithCredential, onAuthStateChanged, signOut } from 'https://www.gstatic.com/firebasejs/12.19.0/firebase-auth.js';

const $ = selector => document.querySelector(selector);
const signInPanel = $('#sign-in-panel'), consolePanel = $('#console-panel');
const signInButton = $('#sign-in'), signOutButton = $('#sign-out'), status = $('#sign-in-status');
const error = $('#error'), activityList = $('#activity-list');
let auth, checking = false, signingIn = false, usingPhoneSession = false;
let activeUpload, pendingApproval;

function showError(message) {
  error.textContent = message;
  error.hidden = false;
}

async function api(path, options = {}) {
  const user = auth?.currentUser;
  if (!user && !usingPhoneSession) throw new Error('Sign in to continue.');
  const headers = new Headers(options.headers || {});
  if (!usingPhoneSession && user) headers.set('authorization', 'Bearer ' + await user.getIdToken());
  if (options.body) headers.set('content-type', 'application/json');
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin', cache: 'no-store' });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || 'The request could not be completed.');
  return result;
}

async function loadConsole(user) {
  if (checking) return;
  checking = true;
  status.textContent = 'Checking your authorization…';
  try {
    const token = user ? await user.getIdToken(true) : null;
    const response = await fetch('/api/admin/session', {
      headers: token ? { authorization: 'Bearer ' + token } : {},
      credentials: 'same-origin',
      cache: 'no-store'
    });
    const result = await response.json();
    if (!response.ok || !result.authorized) {
      if (user) await signOut(auth);
      if (!user && response.status === 401) {
        signInPanel.hidden = false;
        consolePanel.hidden = true;
        status.textContent = 'Access is checked by Rollcall’s server. A regular student account does not grant admin access.';
        return;
      }
      throw new Error(result.error || 'This Google account is not authorized for the Command Center.');
    }
    usingPhoneSession = !user;
    const [overview, campaigns, directory, releaseOperations, releaseDrafts] = await Promise.all([
      api('/api/admin/overview'),
      api('/api/admin/campaigns'),
      api('/api/admin/accounts').catch(problem => ({ error: problem.message || 'Firebase account details are unavailable.' })),
      api('/api/admin/release-operations').catch(problem => ({ error: problem.message || 'GitHub release status is unavailable.' })),
      api('/api/admin/releases').catch(problem => ({ error: problem.message || 'Release Factory is unavailable.' }))
    ]);
    $('#admin-email').textContent = result.email || 'Verified owner account';
    renderFirebaseUserCount(overview.firebaseUsers);
    $('#installation-count').textContent = overview.registeredInstallations;
    renderCampaigns(campaigns.campaigns || []);
    renderAccountDirectory(directory);
    renderReleaseOperations(releaseOperations);
    renderReleaseDrafts(releaseDrafts);
    $('#release-form').hidden = usingPhoneSession;
    $('#release-factory').querySelector('.section-copy').textContent = usingPhoneSession
      ? 'Sign in with an authorized Google account to stage or approve Android releases.'
      : 'Stage a signed APK privately. Validation runs without signing credentials; publication requires a separate email approval code.';
    renderActivity(result.recentAccess || []);
    signInPanel.hidden = true;
    consolePanel.hidden = false;
    error.hidden = true;
  } catch (problem) {
    signInPanel.hidden = false;
    consolePanel.hidden = true;
    status.textContent = 'Access is checked by Rollcall’s server. A regular student account does not grant admin access.';
    showError(problem.message === 'Command Center access is not configured.'
      ? 'Command Center setup is incomplete. The owner account has not been enabled on the server yet.'
      : problem.message || 'Could not verify Command Center access.');
  } finally {
    checking = false;
  }
}

function renderCampaigns(campaigns) {
  const list = $('#campaign-list');
  list.replaceChildren();
  $('#empty-campaigns').hidden = campaigns.length > 0;
  for (const campaign of campaigns) {
    const card = document.createElement('article');
    card.className = 'campaign-item';
    const heading = document.createElement('div');
    heading.className = 'campaign-heading';
    const copy = document.createElement('div');
    const title = document.createElement('h3');
    title.textContent = campaign.title;
    const summary = document.createElement('p');
    summary.textContent = campaign.body;
    copy.append(title, summary);
    const badge = document.createElement('span');
    badge.className = 'campaign-state ' + campaign.status;
    badge.textContent = campaign.status;
    heading.append(copy, badge);
    const meta = document.createElement('p');
    meta.className = 'campaign-meta';
    meta.textContent = campaign.category + ' · ' + campaign.priority + ' · expires ' + new Date(campaign.expiresAt * 1000).toLocaleString()
      + (campaign.channels.includes('popup') ? ' · one-time pop-up' : '')
      + (campaign.channels.includes('push') ? ' · push ' + campaign.pushStatus : ' · app updates');
    card.append(heading, meta);
    const actions = document.createElement('div');
    actions.className = 'campaign-actions';
    if (campaign.status === 'draft') addAction(actions, 'Publish to all eligible', async () => {
      if (!confirm('Publish this announcement to all eligible Rollcall installations? Inbox delivery is persistent. FCM acceptance is not proof of delivery.')) return;
      const result = await api('/api/admin/campaigns/' + campaign.id + '/publish', {
        method: 'POST', body: JSON.stringify({ confirmAllInstallations: true })
      });
      $('#campaign-status').textContent = result.pushStatus === 'failed'
        ? 'Published to the inbox. Push was not accepted; retry it from the campaign.'
        : result.pushStatus === 'accepted' ? 'Published. FCM accepted the push request; phone delivery is not confirmed.'
        : 'Published to the persistent inbox.';
      await refreshConsole();
    }, true);
    if (campaign.status === 'published' && campaign.pushStatus === 'failed') addAction(actions, 'Retry push', async () => {
      const result = await api('/api/admin/campaigns/' + campaign.id + '/retry-push', { method: 'POST', body: '{}' });
      $('#campaign-status').textContent = 'FCM accepted the retry. Delivery to phones is not confirmed.';
      await refreshConsole();
      return result;
    });
    if (campaign.status === 'published') addAction(actions, 'Revoke', async () => {
      if (!confirm('Revoke this announcement? It will disappear from future inbox syncs. Push notifications already displayed cannot be recalled.')) return;
      await api('/api/admin/campaigns/' + campaign.id + '/revoke', {
        method: 'POST', body: JSON.stringify({ confirmRevoke: true })
      });
      $('#campaign-status').textContent = 'Announcement revoked.';
      await refreshConsole();
    });
    card.append(actions);
    list.append(card);
  }
}

function renderAccountDirectory(directory) {
  const users = $('#user-account-list'), admins = $('#admin-account-list');
  users.replaceChildren();
  admins.replaceChildren();
  const accounts = directory?.accounts || [], configuredAdmins = directory?.admins || [];
  $('#account-count').textContent = accounts.length + (accounts.length === 1 ? ' ACCOUNT' : ' ACCOUNTS')
    + (directory?.hasMore ? ' · LATEST 500' : '');
  $('#empty-user-accounts').hidden = accounts.length > 0 || Boolean(directory?.error);
  $('#empty-admin-accounts').hidden = configuredAdmins.length > 0;
  $('#account-directory-status').textContent = directory?.error
    ? directory.error
    : 'Created and last sign-in times are from Firebase Authentication.';
  for (const account of accounts) {
    const item = document.createElement('li');
    const identity = document.createElement('div');
    identity.className = 'account-identity';
    const name = document.createElement('strong');
    name.textContent = account.displayName || account.email || account.uid;
    const email = document.createElement('span');
    email.textContent = (account.email || account.uid) + (account.emailVerified ? ' · verified' : ' · unverified');
    const providers = document.createElement('span');
    providers.textContent = 'Sign-in: ' + (account.providers?.length ? account.providers.join(', ') : 'provider unavailable');
    identity.append(name, email, providers);
    const times = document.createElement('div');
    times.className = 'account-meta';
    const created = document.createElement('div');
    created.textContent = 'Created ' + formatFirebaseTime(account.createdAt);
    const lastLogin = document.createElement('div');
    lastLogin.textContent = 'Last sign-in ' + formatFirebaseTime(account.lastLoginAt);
    times.append(created, lastLogin);
    item.append(identity, times);
    users.append(item);
  }
  for (const admin of configuredAdmins) {
    const item = document.createElement('li');
    const identity = document.createElement('div');
    identity.className = 'account-identity';
    const label = document.createElement('strong');
    label.textContent = admin.email || 'Admin identity';
    const uid = document.createElement('span');
    uid.textContent = admin.uid ? 'UID ' + admin.uid : 'Email allowlist';
    identity.append(label, uid);
    item.append(identity);
    admins.append(item);
  }
}

function formatFirebaseTime(value) {
  if (!value) return 'not available';
  const numeric = Number(value);
  const date = Number.isFinite(numeric) && numeric > 0 ? new Date(numeric) : new Date(value);
  return Number.isNaN(date.getTime()) ? 'not available' : date.toLocaleString();
}

function addAction(parent, label, action, primary = false) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = primary ? 'small-action primary-action' : 'small-action';
  button.textContent = label;
  button.addEventListener('click', async () => {
    button.disabled = true;
    $('#campaign-status').textContent = '';
    error.hidden = true;
    try { await action(); }
    catch (problem) { showError(problem.message || 'The campaign action failed.'); }
    finally { button.disabled = false; }
  });
  parent.append(button);
}

async function refreshConsole() {
  const [overview, campaigns, directory, releaseOperations, releaseDrafts] = await Promise.all([
    api('/api/admin/overview'),
    api('/api/admin/campaigns'),
    api('/api/admin/accounts').catch(problem => ({ error: problem.message || 'Firebase account details are unavailable.' })),
    api('/api/admin/release-operations').catch(problem => ({ error: problem.message || 'GitHub release status is unavailable.' })),
    api('/api/admin/releases').catch(problem => ({ error: problem.message || 'Release Factory is unavailable.' }))
  ]);
  renderFirebaseUserCount(overview.firebaseUsers);
  $('#installation-count').textContent = overview.registeredInstallations;
  renderCampaigns(campaigns.campaigns || []);
  renderAccountDirectory(directory);
  renderReleaseOperations(releaseOperations);
  renderReleaseDrafts(releaseDrafts);
}

function releaseStatus(message) { $('#release-factory-status').textContent = message; }

async function refreshReleaseDrafts() {
  renderReleaseDrafts(await api('/api/admin/releases'));
}

function addReleaseAction(parent, label, action, primary = false) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = primary ? 'small-action primary-action' : 'small-action';
  button.textContent = label;
  button.addEventListener('click', async () => {
    button.disabled = true;
    error.hidden = true;
    try { await action(); await refreshReleaseDrafts(); }
    catch (problem) { showError(problem.message || 'Release action failed.'); }
    finally { button.disabled = false; }
  });
  parent.append(button);
}

function renderReleaseDrafts(data) {
  const list = $('#release-draft-list');
  list.replaceChildren();
  const rows = data?.releases || [];
  $('#empty-release-drafts').hidden = rows.length > 0 || Boolean(data?.error);
  if (data?.error) releaseStatus(data.error);
  for (const release of rows) {
    const item = document.createElement('li'), heading = document.createElement('div');
    heading.className = 'release-row-heading';
    const title = document.createElement('strong');
    title.textContent = release.versionName ? `Rollcall ${release.versionName} · ${release.fileName}` : release.fileName || 'New release draft';
    const state = document.createElement('span');
    state.className = 'release-state ' + (release.status === 'published' ? 'published' : release.status === 'failed' ? 'failed' : 'running');
    state.textContent = release.status.replaceAll('_', ' ');
    heading.append(title, state);
    const details = document.createElement('p');
    details.textContent = [release.versionCode ? `Version code ${release.versionCode}` : '', release.sizeBytes ? formatBytes(release.sizeBytes) : '',
      release.sha256 ? `SHA-256 ${release.sha256}` : '', release.minSdk ? `Android API ${release.minSdk}+` : '',
      release.failureReason || '', release.pushStatus === 'failed' ? 'Push hint was not accepted; the GitHub release remains available.' : '']
      .filter(Boolean).join(' · ');
    item.append(heading, details);
    const note = document.createElement('p');
    note.textContent = release.notes;
    item.append(note);
    const actions = document.createElement('div');
    actions.className = 'campaign-actions';
    if (!usingPhoneSession && ['uploaded', 'failed'].includes(release.status)) addReleaseAction(actions, 'Run validation', async () => {
      await api(`/api/admin/releases/${release.id}/validate`, { method: 'POST', body: '{}' });
      releaseStatus('Validation queued. The GitHub workflow will inspect the signed APK.');
    }, true);
    if (!usingPhoneSession && release.status === 'validated') addReleaseAction(actions, 'Send approval code', async () => {
      await api(`/api/admin/releases/${release.id}/approval-code`, { method: 'POST', body: '{}' });
      pendingApproval = release;
      $('#release-approval-details').textContent = `Version ${release.versionName} (${release.versionCode}). SHA-256 ${release.sha256}. Type v${release.versionName} and enter the code sent to your verified admin email.`;
      $('#release-approval-form').reset();
      $('#release-approval-dialog').showModal();
      releaseStatus('Approval code sent to your verified admin email.');
    }, true);
    if (!usingPhoneSession && ['draft', 'uploaded', 'validated', 'failed'].includes(release.status)) addReleaseAction(actions, 'Cancel draft', async () => {
      if (!confirm('Cancel this release draft and remove its staged APK?')) return;
      await api(`/api/admin/releases/${release.id}/cancel`, { method: 'POST', body: '{}' });
      releaseStatus('Release draft cancelled.');
    });
    if (release.releaseUrl) {
      const link = document.createElement('a');
      link.href = release.releaseUrl;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = 'Open GitHub release';
      actions.append(link);
    }
    item.append(actions);
    list.append(item);
  }
}

function uploadReleaseApk(id, file, token) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    activeUpload = request;
    request.open('POST', `/api/admin/releases/${id}/apk`);
    request.setRequestHeader('authorization', 'Bearer ' + token);
    request.setRequestHeader('content-type', 'application/vnd.android.package-archive');
    request.setRequestHeader('x-rollcall-file-name', file.name);
    request.upload.onprogress = event => {
      if (event.lengthComputable) {
        $('#release-progress').value = Math.round(event.loaded / event.total * 100);
        releaseStatus(`Uploading APK · ${$('#release-progress').value}%`);
      }
    };
    request.onload = () => {
      let result;
      try { result = JSON.parse(request.responseText); } catch { result = {}; }
      request.status >= 200 && request.status < 300 ? resolve(result) : reject(new Error(result.error || 'APK upload failed.'));
    };
    request.onerror = () => reject(new Error('APK upload failed. Check your connection and retry.'));
    request.onabort = () => reject(new Error('APK upload cancelled.'));
    request.send(file);
  });
}

function renderReleaseOperations(data) {
  const releases = $('#release-list'), workflows = $('#workflow-list');
  releases.replaceChildren();
  workflows.replaceChildren();
  const releaseRows = data?.releases || [], workflowRows = data?.workflows || [];
  $('#empty-releases').hidden = releaseRows.length > 0;
  $('#empty-workflows').hidden = workflowRows.length > 0;
  $('#release-operations-status').textContent = data?.error
    ? data.error
    : 'Updated just now · ' + releaseRows.length + ' releases · ' + workflowRows.length + ' workflow runs';
  for (const release of releaseRows) {
    const item = document.createElement('li');
    const heading = document.createElement('div');
    heading.className = 'release-row-heading';
    const title = document.createElement('strong');
    title.textContent = release.name + (release.tag ? ' · ' + release.tag : '');
    const state = document.createElement('span');
    state.className = 'release-state ' + (release.draft ? 'draft' : release.prerelease ? 'preview' : 'published');
    state.textContent = release.draft ? 'Draft' : release.prerelease ? 'Preview' : 'Published';
    heading.append(title, state);
    item.append(heading);
    const date = document.createElement('p');
    date.textContent = release.publishedAt ? 'Published ' + formatGithubTime(release.publishedAt) : 'Not published';
    item.append(date);
    for (const asset of release.apk || []) {
      if (!asset.url) continue;
      const link = document.createElement('a');
      link.href = asset.url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = asset.name + ' · ' + formatBytes(asset.size) + ' · ' + asset.downloads + ' downloads';
      item.append(link);
    }
    if (release.url) {
      const link = document.createElement('a');
      link.href = release.url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = 'Open release';
      item.append(link);
    }
    releases.append(item);
  }
  for (const run of workflowRows) {
    const item = document.createElement('li');
    const heading = document.createElement('div');
    heading.className = 'release-row-heading';
    const title = document.createElement('strong');
    title.textContent = run.name + (run.runNumber ? ' · #' + run.runNumber : '');
    const state = document.createElement('span');
    const result = run.status === 'completed' ? (run.conclusion || 'completed') : run.status;
    state.className = 'release-state ' + (result === 'success' ? 'published' : /failure|cancelled|timed_out/.test(result) ? 'failed' : 'running');
    state.textContent = result.replaceAll('_', ' ');
    heading.append(title, state);
    item.append(heading);
    const detail = document.createElement('p');
    detail.textContent = (run.branch || 'unknown branch') + ' · ' + formatGithubTime(run.createdAt);
    item.append(detail);
    if (run.url) {
      const link = document.createElement('a');
      link.href = run.url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.textContent = run.title || 'View workflow';
      item.append(link);
    }
    workflows.append(item);
  }
}

function formatGithubTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? 'time unavailable' : date.toLocaleString();
}

function formatBytes(value) {
  if (!Number.isFinite(value) || value <= 0) return 'size unavailable';
  return value < 1_000_000 ? Math.round(value / 1000) + ' KB' : (value / 1_000_000).toFixed(1) + ' MB';
}

function renderFirebaseUserCount(count) {
  $('#firebase-user-count').textContent = count ?? '—';
  $('#firebase-user-count-status').textContent = count == null
    ? 'Firebase account count is unavailable; check server service-account access.'
    : 'Live count refreshes automatically.';
}

function renderActivity(events) {
  activityList.replaceChildren();
  $('#activity-count').textContent = events.length + (events.length === 1 ? ' EVENT' : ' EVENTS');
  $('#empty-activity').hidden = events.length > 0;
  for (const event of events) {
    const item = document.createElement('li');
    const action = document.createElement('span');
    action.textContent = event.action === 'session_verified' ? 'Owner session verified' : 'Admin action';
    const time = document.createElement('time');
    time.dateTime = new Date(event.created_at * 1000).toISOString();
    time.textContent = new Date(event.created_at * 1000).toLocaleString();
    item.append(action, time);
    activityList.append(item);
  }
}

const config = window.ROLLCALL_FIREBASE_CONFIG || {};
if (!config.apiKey || !config.projectId || !config.appId) {
  showError('Firebase sign-in is not configured for this site.');
} else {
  auth = getAuth(initializeApp(config));
  onAuthStateChanged(auth, user => loadConsole(user));
  window.addEventListener('load', () => {
    if (!window.google?.accounts?.id || !window.ROLLCALL_GOOGLE_CLIENT_ID) {
      showError('Google sign-in could not load. Refresh the page and try again.');
      return;
    }
    google.accounts.id.initialize({ client_id: window.ROLLCALL_GOOGLE_CLIENT_ID, callback: async response => {
      if (signingIn) return;
      signingIn = true;
      status.textContent = 'Verifying your Google account…';
      error.hidden = true;
      try { await signInWithCredential(auth, GoogleAuthProvider.credential(response.credential)); }
      catch (problem) { showError(problem.message || 'Google sign-in could not be completed.'); }
      finally { signingIn = false; }
    } });
    google.accounts.id.renderButton(signInButton, { theme: 'filled_black', size: 'large', width: Math.min(480, signInButton.clientWidth), text: 'continue_with' });
    google.accounts.id.prompt();
  });
  signOutButton.addEventListener('click', async () => {
    if (usingPhoneSession) await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' });
    if (auth.currentUser) await signOut(auth);
    usingPhoneSession = false;
    window.google?.accounts?.id?.disableAutoSelect();
    signInPanel.hidden = false;
    consolePanel.hidden = true;
  });
  $('#refresh-campaigns').addEventListener('click', async () => {
    try { await refreshConsole(); }
    catch (problem) { showError(problem.message || 'Could not refresh campaigns.'); }
  });
  $('#refresh-release-operations').addEventListener('click', async () => {
    const button = $('#refresh-release-operations');
    button.disabled = true;
    try { renderReleaseOperations(await api('/api/admin/release-operations')); }
    catch (problem) { renderReleaseOperations({ error: problem.message || 'Could not refresh GitHub status.' }); }
    finally { button.disabled = false; }
  });
  $('#refresh-release-drafts').addEventListener('click', async () => {
    try { await refreshReleaseDrafts(); }
    catch (problem) { releaseStatus(problem.message || 'Could not refresh staged releases.'); }
  });
  $('#cancel-upload').addEventListener('click', () => activeUpload?.abort());
  $('#release-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget, file = form.elements.apk.files?.[0];
    if (!file || file.size < 1_000_000 || file.size > 20 * 1024 * 1024 || !/\.apk$/i.test(file.name)) {
      releaseStatus('Choose a signed APK between 1 MB and 20 MiB.');
      return;
    }
    const button = $('#upload-release');
    button.disabled = true;
    $('#cancel-upload').hidden = false;
    $('#release-progress').hidden = false;
    $('#release-progress').value = 0;
    error.hidden = true;
    let draftId;
    try {
      const draft = await api('/api/admin/releases', { method: 'POST', body: JSON.stringify({
        notes: form.elements.notes.value, channel: 'stable', policy: form.elements.policy.value,
        confirmAllInstallations: form.elements.confirmAll.checked
      }) });
      draftId = draft.release.id;
      const token = await auth.currentUser.getIdToken();
      await uploadReleaseApk(draftId, file, token);
      form.reset();
      releaseStatus('APK staged privately. Review the checksum below, then run validation.');
      await refreshReleaseDrafts();
    } catch (problem) {
      if (draftId) await api(`/api/admin/releases/${draftId}/cancel`, { method: 'POST', body: '{}' }).catch(() => {});
      releaseStatus(problem.message || 'APK could not be staged.');
      await refreshReleaseDrafts().catch(() => {});
    } finally {
      activeUpload = null;
      button.disabled = false;
      $('#cancel-upload').hidden = true;
      $('#release-progress').hidden = true;
    }
  });
  $('#close-release-approval').addEventListener('click', () => $('#release-approval-dialog').close());
  $('#release-approval-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (!pendingApproval) return;
    const form = event.currentTarget, button = $('#confirm-release-publish');
    button.disabled = true;
    try {
      await api(`/api/admin/releases/${pendingApproval.id}/publish`, { method: 'POST', body: JSON.stringify({
        code: form.elements.code.value, confirmTag: form.elements.tag.value.trim()
      }) });
      $('#release-approval-dialog').close();
      pendingApproval = null;
      releaseStatus('Publication queued. The GitHub workflow will publish the validated APK.');
      await refreshReleaseDrafts();
    } catch (problem) { showError(problem.message || 'Release approval failed.'); }
    finally { button.disabled = false; }
  });
  $('#campaign-form').addEventListener('submit', async event => {
    event.preventDefault();
    const campaignForm = event.currentTarget;
    const form = new FormData(campaignForm);
    const expiresLocal = String(form.get('expiresAt') || '');
    const payload = {
      title: String(form.get('title') || ''),
      body: String(form.get('body') || ''),
      category: String(form.get('category') || 'announcement'),
      priority: String(form.get('priority') || 'normal'),
      channels: ['inbox', ...(form.get('popup') ? ['popup'] : []), ...(form.get('push') ? ['push'] : [])],
      actionLabel: String(form.get('actionLabel') || ''),
      actionPath: String(form.get('actionPath') || ''),
      expiresAt: expiresLocal ? new Date(expiresLocal).toISOString() : null
    };
    const button = $('#save-campaign');
    button.disabled = true;
    $('#campaign-status').textContent = '';
    error.hidden = true;
    try {
      await api('/api/admin/campaigns', { method: 'POST', body: JSON.stringify(payload) });
      campaignForm.reset();
      campaignForm.elements.inbox.checked = true;
      $('#campaign-status').textContent = 'Draft saved. Review it below before publishing.';
      await refreshConsole();
    } catch (problem) {
      showError(problem.message || 'Draft could not be saved.');
    } finally { button.disabled = false; }
  });
  window.setInterval(() => {
    if (!document.hidden && !consolePanel.hidden) refreshConsole().catch(() => {});
  }, 30_000);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && !consolePanel.hidden) refreshConsole().catch(() => {});
  });
}
