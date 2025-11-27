#!/usr/bin/env node
const https = require('https');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const crypto = require('crypto');

const manifest = require('../sdk-manifest.json');
const platform = process.argv[2] || (process.platform === 'darwin' ? 'ios' : 'android');

const config = manifest[platform];
if (!config) {
    console.error(`Unknown platform: ${platform}`);
    process.exit(1);
}

const downloadUrl = platform === 'ios' ? config.downloadUrl : config.directDownload.downloadUrl;
const expectedHash = platform === 'ios' ? config.sha256 : config.directDownload.sha256;
const destDir = platform === 'ios' ? 'ios/Flir/libs' : 'android/Flir/libs';

console.log(`Downloading ${platform} SDK...`);

const zipPath = path.join(__dirname, '..', `${platform}-sdk.zip`);
const file = fs.createWriteStream(zipPath);

https.get(downloadUrl, (response) => {
    const total = parseInt(response.headers['content-length'], 10);
    let downloaded = 0;

    response.on('data', (chunk) => {
        downloaded += chunk.length;
        const pct = ((downloaded / total) * 100).toFixed(1);
        process.stdout.write(`\rProgress: ${pct}%`);
    });

    response.pipe(file);

    file.on('finish', () => {
        console.log('\nVerifying checksum...');

        const hash = crypto.createHash('sha256');
        const data = fs.readFileSync(zipPath);
        hash.update(data);
        const actualHash = hash.digest('hex');

        if (actualHash !== expectedHash) {
            console.error('Checksum mismatch!');
            fs.unlinkSync(zipPath);
            process.exit(1);
        }

        console.log('Extracting...');
        fs.mkdirSync(destDir, { recursive: true });
        execSync(`unzip -o "${zipPath}" -d "${destDir}"`);
        fs.unlinkSync(zipPath);

        console.log(`Done! SDK installed to ${destDir}`);
    });
}).on('error', (err) => {
    console.error('Download failed:', err.message);
    process.exit(1);
});
