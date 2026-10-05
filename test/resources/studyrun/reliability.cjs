// Opt-in real-server reliability exercise, invoked by StudyRunReliabilityIntegrationTest.
const {readFileSync, writeFileSync} = require('node:fs');
const assert = require('node:assert/strict');
const {chromium} = require(process.env.JATOS_PLAYWRIGHT_MODULE);
const config = JSON.parse(readFileSync(process.argv[2], 'utf8'));
(async () => {
    const browser = await chromium.launch();
    try {
        const runs = await Promise.all(config.codes.map(async (code, index) => {
            const context = await browser.newContext();
            try {
                const page = await context.newPage();
                const errors = [];
                page.on('pageerror', error => errors.push(error.message));
                // Keep access to the real sockets so closure exercises the actual reconnect path.
                await page.addInitScript(() => {
                    const NativeWebSocket = window.WebSocket;
                    window.testSockets = [];
                    window.WebSocket = class extends NativeWebSocket {
                        constructor(...args) { super(...args); window.testSockets.push(this); }
                    };
                });
                await page.goto(config.base + '/publix/' + code);
                await page.evaluate(() => new Promise(resolve => jatos.onLoad(resolve)));
                const uuid = await page.evaluate(() => jatos.studyResultUuid);
                // Concurrent clients update separate keys in one batch, then reconnect twice.
                await page.evaluate(async index => {
                    jatos.batchSessionVersioning = false;
                    for (let attempt = 0; ; attempt++) {
                        try {
                            await jatos.batchSession.set('client' + index, index);
                            break;
                        } catch (error) {
                            // The server deliberately bounds compare-and-set retries under contention.
                            if (attempt === 4 || !String(error).includes('concurrent updates')) throw error;
                            console.log('Retrying explicit batch conflict', index, attempt + 1);
                            await new Promise(resolve => setTimeout(resolve, 50 * (index + 1)));
                        }
                    }
                }, index);
                for (let round = 0; round < 2; round++) {
                    const count = await page.evaluate(() => {
                        const count = testSockets.length;
                        testSockets.at(-1).close();
                        return count;
                    });
                    await page.waitForFunction(count => testSockets.length > count &&
                        testSockets.at(-1).readyState === WebSocket.OPEN, count, {timeout: 30000});
                    await page.waitForFunction(index => jatos.batchSession.get('client' + index) === index, index);
                }
                // Prepare data before disconnecting so the result request starts while offline.
                await page.evaluate(index => {
                    let state = index + 1;
                    let payload = '';
                    for (let i = 0; i < 1024 * 1024; i++) {
                        state = (Math.imul(state, 1664525) + 1013904223) >>> 0;
                        payload += String.fromCharCode(33 + (state >>> 16) % 90);
                    }
                    window.testPayload = payload;
                }, index);
                await context.setOffline(true);
                let settled = false;
                const pending = page.evaluate(async index => {
                    jatos.httpRetryWait = 1000;
                    let payload = window.testPayload;
                    await jatos.submitResultData(payload);
                    for (let i = 0; i < 10; i++) {
                        const suffix = '\nclient-' + index + '-part-' + i;
                        await jatos.appendResultData(suffix);
                        payload += suffix;
                    }
                    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(payload));
                    return {length: payload.length, hash: [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('')};
                }, index).finally(() => { settled = true; });
                await new Promise(resolve => setTimeout(resolve, 300));
                assert.equal(settled, false, 'Result submission must wait while offline');
                await context.setOffline(false);
                const expected = await pending;
                // Send an incomplete HTTP body and disconnect: the server must not persist partial data.
                const cookies = (await context.cookies()).map(c => `${c.name}=${c.value}`).join('; ');
                const target = new URL(page.url().replace(/\/start(?:\?.*)?$/, '/resultData'));
                await new Promise((resolve, reject) => {
                    const socket = require('node:net').connect(Number(target.port), target.hostname, () => {
                        socket.write(`PUT ${target.pathname} HTTP/1.1\r\nHost: ${target.host}\r\nCookie: ${cookies}\r\nContent-Type: text/plain\r\nContent-Length: 100000\r\nConnection: close\r\n\r\nPARTIAL`, () => {
                            setTimeout(() => socket.destroy(), 100);
                        });
                    });
                    socket.setTimeout(5000, () => socket.destroy(new Error('Truncated request timeout')));
                    socket.on('error', reject);
                    socket.on('close', resolve);
                });
                await page.evaluate(() => jatos.endStudyAjax(true, 'Reliability exercise'));
                assert.deepEqual(errors, []);
                console.log('PASS concurrent client', index, uuid);
                return {uuid, ...expected};
            } finally { await context.close(); }
        }));
        writeFileSync(config.output, JSON.stringify(runs));
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
