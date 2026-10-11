// Points the download button at the latest release and counts presses.
// The counter is one Firestore number; the rules only allow adding 1, and nothing about the visitor is sent.

const RELEASE_API = 'https://api.github.com/repos/Kolgrim33/centiumvpn/releases/latest';
const APK_NAME = /^centium-.*\.apk$/;

const local = ['localhost', '127.0.0.1'].includes(location.hostname);
const FIRESTORE = local
  ? 'http://127.0.0.1:8085/v1/projects/demo-centium/databases/(default)/documents'
  : 'https://firestore.googleapis.com/v1/projects/centium-app/databases/(default)/documents';
// Firebase web API keys identify the project; they aren't secrets. Access is governed by firestore.rules.
const API_KEY = 'AIzaSyBDwYS_a32usXBNglIl15rCRi8tFF6N6dA';

const button = document.getElementById('download');

async function loadLatest() {
  try {
    const res = await fetch(RELEASE_API, { headers: { Accept: 'application/vnd.github+json' } });
    if (!res.ok) return;
    const release = await res.json();
    const apk = (release.assets || []).find((a) => APK_NAME.test(a.name));
    if (!apk) return;
    button.href = apk.browser_download_url;
    document.getElementById('version').textContent = `Version ${release.tag_name.replace(/^v/, '')}`;
    if (apk.digest && apk.digest.startsWith('sha256:')) {
      document.getElementById('sha').textContent = apk.digest.slice('sha256:'.length);
      document.getElementById('checksum').hidden = false;
    }
  } catch {
    // Offline or rate-limited: the button keeps pointing at the releases page.
  }
}

function countPress() {
  const name = FIRESTORE.replace(/^.*\/v1\//, '') + '/stats/downloads';
  const body = {
    writes: [{
      transform: { document: name, fieldTransforms: [{ fieldPath: 'count', increment: { integerValue: '1' } }] },
      currentDocument: { exists: true },
    }],
  };
  // keepalive lets the request finish even if the page navigates away.
  fetch(`${FIRESTORE}:commit?key=${API_KEY}`, {
    method: 'POST',
    keepalive: true,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).catch(() => {});
}

button.addEventListener('click', countPress);
loadLatest();
