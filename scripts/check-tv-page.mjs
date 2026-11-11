// Checks the AES-256-GCM of the TV's camera page (app/src/main/res/raw/tv_page.html) against Node's own:
// the page cannot use crypto.subtle without https, so it carries its own code (D-163, step 2б).
// Run: node scripts/check-tv-page.mjs
import { createDecipheriv, createCipheriv, randomBytes } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import assert from 'node:assert/strict';

const html = readFileSync(new URL('../app/src/main/res/raw/tv_page.html', import.meta.url), 'utf8');
const page = {};
runInNewContext(html.match(/<script>([\s\S]*?)<\/script>/)[1] + '; this.seal = seal;', page);

function node(key, nonce, plain) {
    const cipher = createCipheriv('aes-256-gcm', key, nonce);
    return Buffer.concat([nonce, cipher.update(plain), cipher.final(), cipher.getAuthTag()]);
}

// SP 800-38D / McGrew–Viega test case 16 without its additional data: key, nonce and text from the paper.
const key = Buffer.from('feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308', 'hex');
const nonce = Buffer.from('cafebabefacedbaddecaf888', 'hex');
const text = Buffer.from('d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b39', 'hex');
assert.equal(Buffer.from(page.seal(key, nonce, text)).toString('hex'), node(key, nonce, text).toString('hex'));

// Up to 300 bytes, and past 255 blocks, where the block counter carries into its next byte.
for (const length of [...Array(301).keys(), 4097, 16 * 1024]) {
    const key = randomBytes(32), nonce = randomBytes(12), plain = randomBytes(length);
    const sealed = Buffer.from(page.seal(new Uint8Array(key), new Uint8Array(nonce), new Uint8Array(plain)));
    assert.deepEqual(sealed, node(key, nonce, plain), `length ${length}`);

    const decipher = createDecipheriv('aes-256-gcm', key, nonce);
    decipher.setAuthTag(sealed.subarray(sealed.length - 16));
    assert.deepEqual(Buffer.concat([decipher.update(sealed.subarray(12, sealed.length - 16)), decipher.final()]), plain);
}

console.log('tv_page.html: AES-256-GCM matches Node for 0–300 bytes, 4 KB and 16 KB');
