// Prints the website's download-button count and GitHub's own APK download total.
// Uses the Firebase CLI's login (run `firebase login` first); the count isn't publicly readable.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';

const PROJECT = JSON.parse(readFileSync(new URL('../.firebaserc', import.meta.url))).projects.default;
const DOC = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents/stats/downloads`;
const RELEASES = 'https://api.github.com/repos/Kolgrim33/centiumvpn/releases?per_page=100';

function accessToken() {
  // Refreshes the CLI's token if it has expired.
  execFileSync('firebase', ['projects:list', '--json'], { stdio: 'ignore' });
  const config = JSON.parse(readFileSync(`${homedir()}/.config/configstore/firebase-tools.json`, 'utf8'));
  return config.tokens.access_token;
}

async function websiteCount() {
  const res = await fetch(DOC, { headers: { Authorization: `Bearer ${accessToken()}` } });
  if (!res.ok) throw new Error(`Firestore ${res.status}: ${await res.text()}`);
  return Number((await res.json()).fields.count.integerValue);
}

async function githubCount() {
  const res = await fetch(RELEASES, { headers: { Accept: 'application/vnd.github+json' } });
  if (!res.ok) throw new Error(`GitHub ${res.status}`);
  return (await res.json())
    .flatMap((r) => r.assets)
    .filter((a) => /^centium-.*\.apk$/.test(a.name))
    .reduce((sum, a) => sum + a.download_count, 0);
}

const [site, github] = await Promise.all([websiteCount(), githubCount()]);
console.log(`Download button presses on centium.web.app: ${site}`);
console.log(`APK downloads on GitHub (includes in-app updates): ${github}`);
