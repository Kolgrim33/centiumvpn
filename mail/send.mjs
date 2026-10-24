// Emails Centium's Play Store testers as "Centium Team" through a Gmail account.
//
//   node send.mjs invite                     ask testers to opt in at the Play testing link
//   node send.mjs release 1.2.4 notes.md     tell testers a new version is out
//   add --dry-run to print what would be sent without sending
//
// Env: SMTP_USER (the Gmail address), SMTP_PASS (its app password), and either
// TESTER_EMAILS (comma or newline separated) or TESTER_EMAILS_FILE (one per line).
// The repo is public, so the list never lives in it: CI reads it from a secret.
import { readFileSync } from 'node:fs';
import nodemailer from 'nodemailer';

const TESTING_URL = 'https://play.google.com/apps/testing/org.centium.vpn';
const STORE_URL = 'https://play.google.com/store/apps/details?id=org.centium.vpn';
const SITE_URL = 'https://centium.web.app';
// Gmail won't show SVG, so the header uses a PNG of assets/logo/centium-icon-512.png hosted with the site.
const LOGO_URL = `${SITE_URL}/email-logo.png`;

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run');
const [mode, version, notesPath] = args.filter((a) => a !== '--dry-run');

function recipients() {
  const raw = process.env.TESTER_EMAILS
    ?? (process.env.TESTER_EMAILS_FILE && readFileSync(process.env.TESTER_EMAILS_FILE, 'utf8'));
  if (!raw) throw new Error('Set TESTER_EMAILS or TESTER_EMAILS_FILE');
  const emails = raw.split(/[\s,;]+/).map((e) => e.trim().toLowerCase()).filter((e) => e.includes('@'));
  return [...new Set(emails)];
}

const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
// Notes may mark a lead-in as **bold**; nothing else is markdown.
const inline = (s) => escape(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>');
const plain = (s) => s.replace(/\*\*(.+?)\*\*/g, '$1');

// Wraps a message body in the brand frame: the purple route colour and plain sentence-case copy.
function frame(heading, bodyHtml) {
  return `<!doctype html><html><body style="margin:0;background:#F6F7F9;font-family:Arial,Helvetica,sans-serif;color:#161C27">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:32px 16px">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#FFFFFF;border-radius:12px">
<tr><td style="padding:24px 28px 0"><table role="presentation" cellpadding="0" cellspacing="0"><tr>
<td><img src="${LOGO_URL}" width="44" height="44" alt="" style="display:block;border:0;border-radius:10px"></td>
<td style="padding-left:12px;font-size:20px;font-weight:bold;color:#161C27">Centium</td></tr></table></td></tr>
<tr><td style="padding:28px">
<h1 style="margin:0 0 16px;font-size:22px">${heading}</h1>
${bodyHtml}
<p style="margin:28px 0 0;font-size:15px">Thanks for testing,<br>The Centium Team</p>
</td></tr></table>
<p style="max-width:520px;margin:16px 0 0;font-size:12px;color:#586274;line-height:1.5">You're getting this because you signed up to test Centium VPN.
Hit a problem? Just reply to this email. To stop these emails, leave the test at <a href="${TESTING_URL}" style="color:#586274">${TESTING_URL}</a>.</p>
</td></tr></table></body></html>`;
}

const button = (url, label) =>
  `<p style="margin:24px 0"><a href="${url}" style="display:inline-block;background:#4A3FD0;color:#FFFFFF;text-decoration:none;font-weight:bold;padding:12px 22px;border-radius:8px">${label}</a></p>`;

function inviteMessage() {
  const subject = "You're invited to test Centium VPN";
  const html = frame("You're on the Centium tester list", `
<p style="font-size:15px;line-height:1.6">Centium sends all your phone's traffic through Tor, so the sites you visit can't see where you are.</p>
<p style="font-size:15px;line-height:1.6">To get the test version from Google Play, open the link below <b>while signed in to this Google account</b> and tap <b>Become a tester</b>. Then install Centium from the Play Store.</p>
${button(TESTING_URL, 'Become a tester')}
<p style="font-size:15px;line-height:1.6">If the Play Store says the app isn't available, wait a few minutes after joining and try again.</p>`);
  const text = `You're on the Centium tester list.

Centium sends all your phone's traffic through Tor, so the sites you visit can't see where you are.

To get the test version from Google Play, open this link while signed in to this Google account and tap "Become a tester":
${TESTING_URL}

Then install Centium from the Play Store: ${STORE_URL}

Thanks for testing,
The Centium Team

Hit a problem? Just reply to this email. To stop these emails, leave the test at ${TESTING_URL}`;
  return { subject, html, text };
}

function releaseMessage(version, notes) {
  const items = notes.split('\n').map((l) => l.trim()).filter((l) => l.startsWith('- ')).map((l) => l.slice(2));
  const subject = `Centium ${version} is here`;
  const html = frame(`Centium ${version} is here`, `
<p style="font-size:15px;line-height:1.6">A new version of Centium is now on Google Play. Here's what's new:</p>
<ul style="font-size:15px;line-height:1.6;padding-left:20px">${items.map((i) => `<li style="margin-bottom:10px">${inline(i)}</li>`).join('')}</ul>
<h2 style="margin:28px 0 8px;font-size:17px">How to update</h2>
<p style="font-size:15px;line-height:1.6;margin:0">Tap the button below, or open the <b>Play Store</b> app, search for <b>Centium VPN</b> and tap <b>Update</b>.</p>
${button(STORE_URL, 'Update on Google Play')}
<p style="font-size:14px;line-height:1.6;color:#586274">Don't see an update yet? Google can take a day to reach every phone. If it still isn't there, check that you've joined the test at <a href="${TESTING_URL}" style="color:#4A3FD0">this link</a>.</p>`);
  const text = `Centium ${version} is here

A new version of Centium is now on Google Play. Here's what's new:

${items.map((i) => `- ${plain(i)}`).join('\n\n')}

How to update
Open the Play Store app, search for Centium VPN and tap Update, or use this link:
${STORE_URL}

Don't see an update yet? Google can take a day to reach every phone. If it still isn't there, check that you've joined the test: ${TESTING_URL}

Thanks for testing,
The Centium Team

Hit a problem? Just reply to this email. To stop these emails, leave the test at ${TESTING_URL}
${SITE_URL}`;
  return { subject, html, text };
}

let message;
if (mode === 'invite') message = inviteMessage();
else if (mode === 'release' && version && notesPath) message = releaseMessage(version, readFileSync(notesPath, 'utf8'));
else {
  console.error('Usage: node send.mjs invite | release <version> <notes.md> [--dry-run]');
  process.exit(2);
}

const to = recipients();
if (dryRun) {
  console.log(`Subject: ${message.subject}\nRecipients: ${to.length}\n\n${message.text}`);
  process.exit(0);
}

const { SMTP_USER, SMTP_PASS } = process.env;
if (!SMTP_USER || !SMTP_PASS) throw new Error('Set SMTP_USER and SMTP_PASS');
const transport = nodemailer.createTransport({ service: 'gmail', auth: { user: SMTP_USER, pass: SMTP_PASS } });

// One message per tester, so nobody sees anyone else's address.
let failed = 0;
for (const address of to) {
  try {
    await transport.sendMail({ from: { name: 'Centium Team', address: SMTP_USER }, to: address, ...message });
  } catch (err) {
    failed++;
    console.error(`Failed: ${address.replace(/^(.{2}).*@/, '$1***@')}: ${err.message}`);
  }
}
console.log(`Sent ${to.length - failed} of ${to.length}`);
process.exit(failed ? 1 : 0);
