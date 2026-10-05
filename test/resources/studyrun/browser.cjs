// Opt-in browser integration, invoked by StudyRunIntegrationTest.
const {readFileSync, writeFileSync} = require('node:fs');
const assert = require('node:assert/strict');
const playwright = require(process.env.JATOS_PLAYWRIGHT_MODULE);
const config = JSON.parse(readFileSync(process.argv[2], 'utf8'));
(async () => {
    const runs = [];
    let codeIndex = 0;
    for (const browserName of ['chromium', 'firefox', 'webkit']) {
        const browser = await playwright[browserName].launch({headless: true});
        try {
            for (const mode of ['current', 'minified', 'abort']) {
                const context = await browser.newContext();
                const page = await context.newPage();
                const errors = [];
                page.on('pageerror', error => { errors.push(error.message); console.error('PAGE ERROR', browserName, mode, error.message); });
                page.on('response', response => { if (response.status() >= 400) console.error('HTTP', response.status(), response.url()); });
                page.on('console', message => { if (message.type() === 'error') console.error('CONSOLE', message.text()); });
                if (mode === 'minified') {
                    await page.route(/\/jatos\.js(?:\?.*)?$/, async route => {
                        const response = await route.fetch({url: route.request().url().replace('/jatos.js', '/jatos.min.js')});
                        await route.fulfill({response});
                    });
                }
                const response = await page.goto(config.base + '/publix/' + config.codes[codeIndex++] + '?compat=Gr%C3%BC%C3%9Fe', {waitUntil: 'load'});
                assert.equal(response.status(), 200);
                const ready = async () => {
                    await page.waitForFunction(() => window.jatos && typeof jatos.onLoad === 'function');
                    await page.evaluate(() => new Promise((resolve, reject) => {
                        const timer = setTimeout(() => reject(new Error('jatos.onLoad did not complete')), 15000);
                        jatos.onLoad(() => { clearTimeout(timer); resolve(); });
                    }));
                };
                try { await ready(); } catch (error) {
                    console.error('INIT STATE', await page.evaluate(() => ({url: location.href, jquery: window.jatos?.jQuery?.fn?.jquery, studyResultUuid: window.jatos?.studyResultUuid, properties: window.jatos?.studyProperties})));
                    throw error;
                }
                const uuid = await page.evaluate(() => jatos.studyResultUuid);
                if (mode === 'abort') {
                    await page.evaluate(() => jatos.abortStudyAjax('Compatibility abort'));
                } else {
                    const initial = await page.evaluate(() => ({title: jatos.studyProperties.title, countries: jatos.componentJsonInput.countries}));
                    assert.equal(initial.title, 'Potato Compass');
                    assert(initial.countries.includes('Germany'));
                    await page.evaluate(async () => {
                        await jatos.submitResultData({text: 'Grüße 日本語 😀', score: 7});
                        await jatos.appendResultData('\nappend');
                        await jatos.setStudySessionData({carry: 'Übertrag 😀'});
                        await jatos.uploadResultFile('file Grüße 😀', 'compat.txt');
                    });
                    const fileResponse = await page.evaluate(async () => {
                        const response = await fetch('../files/compat.txt');
                        return {status: response.status, body: await response.text()};
                    });
                    assert.equal(fileResponse.status, 200);
                    assert.equal(fileResponse.body, 'file Grüße 😀');
                    for (let position = 2; position <= 3; position++) {
                        const previous = page.url();
                        await page.evaluate(() => { jatos.startNextComponent(); });
                        await page.waitForURL(url => url.href !== previous);
                        await ready();
                        assert.equal(await page.evaluate(() => jatos.studySessionData.carry), 'Übertrag 😀');
                        await page.evaluate(pos => jatos.submitResultData({component: pos}), position);
                    }
                    await page.evaluate(() => jatos.endStudyAjax(true, 'Compatibility finished'));
                }
                runs.push({uuid, browser: browserName, mode});
                assert.deepEqual(errors, [], `${browserName}/${mode}: uncaught study errors`);
                console.log(`PASS ${browserName}/${mode}: ${uuid}`);
                await context.close();
            }
        } finally { await browser.close(); }
    }
    writeFileSync(config.output, JSON.stringify(runs));
})().catch(error => { console.error(error); process.exitCode = 1; });
