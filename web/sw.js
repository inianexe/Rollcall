const CACHE = 'rollcall-shell-v11';
const FILES = ['./', './index.html', './styles.css', './app.js', './cloud-auth.js', './shape-grid.js', './firebase-config.js', './manifest.webmanifest', './assets/rollcall-brush.png', './assets/JetBrainsMono-Regular.ttf', './assets/JetBrainsMono-Bold.ttf'];
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES)).then(() => self.skipWaiting())));
self.addEventListener('activate', event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim())));
self.addEventListener('notificationclick', event => event.waitUntil((async () => { event.notification.close(); const clients = await self.clients.matchAll({type:'window',includeUncontrolled:true}); const client = clients.find(item => 'focus' in item); if (client) return client.focus(); return self.clients.openWindow('./'); })()));
self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET' || new URL(event.request.url).origin !== self.location.origin) return;
  if (new URL(event.request.url).pathname.startsWith('/api/')) return;
  event.respondWith(caches.match(event.request).then(cached => cached || fetch(event.request).then(response => {
    const copy = response.clone(); caches.open(CACHE).then(cache => cache.put(event.request, copy)); return response;
  }).catch(() => caches.match('./index.html'))));
});
