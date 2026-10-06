const assert = require('node:assert/strict');
const {readFileSync, writeFileSync} = require('node:fs');
const {execFileSync, execFile} = require('node:child_process');
const {promisify} = require('node:util');
const path = require('node:path');
const playwright = require(process.env.JATOS_PLAYWRIGHT_MODULE);
const project = process.env.JATOS_IT_PROJECT;
assert.match(project || '', /^jatos-it-[0-9]+-[0-9]+$/);
const composeArgs = ['compose', '-p', project, '-f', path.resolve('test/multinode/compose.yaml')];
const compose = (...args) => execFileSync('docker', [...composeArgs, ...args], {encoding: 'utf8'}).trim();
const composeAsync = (...args) => promisify(execFile)('docker', [...composeArgs, ...args], {timeout: 120000});
const base = service => 'http://' + compose('port', service, service === 'load-balancer' ? '80' : '9000');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(label, check, timeout = 120000) {
    const deadline = Date.now() + timeout;
    let last;
    while (Date.now() < deadline) {
        try { if (await check()) return; } catch (error) { last = error; }
        await sleep(500);
    }
    throw new Error(`Timed out: ${label}${last ? ': ' + last.message : ''}`);
}
const watchdog = setTimeout(() => { console.error('Multi-node test exceeded 12 minutes'); process.exit(1); }, 720000);
(async () => {
    const seed = base('jatos-seed'), worker = base('jatos'), proxy = base('load-balancer');
    assert.notEqual(seed, worker);
    const browser = await playwright.chromium.launch();
    const contexts = [];
    const pages = [];
    const evidence = {nodes: {seed, worker, proxy}, checks: []};
    const pass = message => { evidence.checks.push(message); console.log('PASS', message); };
    try {
        const admin = await playwright.request.newContext();
        contexts.push(admin);
        const signin = await admin.get(seed + '/jatos/signin');
        assert.equal(signin.status(), 200);
        const csrf = (await signin.text()).match(/'Csrf-Token':\s*'([^']+)'/)[1];
        let response = await admin.post(seed + '/jatos/signin/local', {
            form: {username: 'admin', password: 'multi-node-test-password'}, headers: {'Csrf-Token': csrf}
        });
        assert.equal(response.status(), 200, await response.text());
        response = await admin.get(seed + '/jatos/api/v1/users');
        assert.equal(response.status(), 200, await response.text());
        const user = (await response.json()).data.find(u => u.username === 'admin');
        assert(user);
        response = await admin.post(seed + `/jatos/api/v1/users/${user.id}/tokens`, {
            data: {name: 'isolated multi-node integration'}, headers: {'Csrf-Token': csrf}
        });
        assert.equal(response.status(), 201, await response.text());
        const token = (await response.json()).data.token;
        async function api(url, method = 'GET', options = {}) {
            const result = await admin.fetch(url, {method, ...options,
                headers: {...options.headers, Authorization: 'Bearer ' + token}, timeout: 30000});
            assert(result.ok(), `${method} ${url}: ${result.status()} ${await result.text()}`);
            return result;
        }
        response = await api(seed + '/jatos/api/v1/studies', 'POST', {multipart: {
            study: {name: 'potato_compass.jzip', mimeType: 'application/zip', buffer: readFileSync('test/resources/potato_compass.jzip')}
        }});
        const study = (await response.json()).data;
        await api(seed + `/jatos/api/v1/studies/${study.id}/properties`, 'PATCH', {data: {groupStudy: true}});
        const batches = (await (await api(seed + `/jatos/api/v1/studies/${study.id}/batches`)).json()).data;
        const batch = batches[0];
        await api(seed + `/jatos/api/v1/batches/${batch.id}`, 'PATCH', {data: {maxActiveMembers: 3, maxTotalMembers: 3}});
        const codes = (await (await api(seed + `/jatos/api/v1/studies/${study.id}/studyCodes`, 'POST', {
            data: {type: 'PersonalSingle', amount: 3}
        })).json()).data;
        const members = [];
        for (const [i, origin] of [seed, worker, seed].entries()) {
            const context = await browser.newContext();
            contexts.push(context);
            const page = await context.newPage();
            pages.push(page);
            await page.goto(origin + '/publix/' + codes[i]);
            await page.evaluate(() => new Promise(resolve => jatos.onLoad(resolve)));
            members.push(await page.evaluate(() => ({uuid: jatos.studyResultUuid, id: jatos.studyResultId})));
        }
        // Successful cross-node publication is the readiness gate; a healthy HTTP port alone is insufficient.
        await until('cross-node batch subscription', async () => {
            await pages[0].evaluate(() => { jatos.batchSessionVersioning = false; return jatos.batchSession.set('ready', true); });
            return pages[1].evaluate(() => jatos.batchSession.get('ready') === true);
        });
        for (const page of pages) {
            await page.evaluate(() => {
                window.received = []; window.channelErrors = [];
                return jatos.joinGroup({onMessage: msg => received.push(msg), onError: error => channelErrors.push(String(error))});
            });
        }
        await until('three group channels across two nodes', async () =>
            (await Promise.all(pages.map(p => p.evaluate(() => jatos.groupChannels.length)))).every(n => n === 3));
        const groupIds = await Promise.all(pages.map(p => p.evaluate(() => jatos.groupResultId)));
        assert.equal(new Set(groupIds).size, 1);
        for (let i = 0; i < pages.length; i++) members[i].memberId = await pages[i].evaluate(() => jatos.groupMemberId);
        pass('participants on two explicit nodes share one batch and group');

        async function messaging(label) {
            await pages[0].evaluate(label => jatos.batchSession.set('stage', label), label);
            await until('batch propagation ' + label, async () => pages[1].evaluate(label => jatos.batchSession.get('stage') === label, label));
            await pages[1].evaluate(label => jatos.groupSession.set('stage', label), label);
            await until('group propagation ' + label, async () => pages[0].evaluate(label => jatos.groupSession.get('stage') === label, label));
            await Promise.all(pages.map(p => p.evaluate(() => { received.length = 0; channelErrors.length = 0; })));
            await pages[0].evaluate(({recipient, label}) => {
                for (let seq = 0; seq < 5; seq++) jatos.sendGroupMsgTo(recipient, {label, seq, kind: 'direct'});
            }, {recipient: members[1].memberId, label});
            await until('direct messages ' + label, async () => (await pages[1].evaluate(() => received.length)) === 5);
            await pages[1].evaluate(label => jatos.sendGroupMsg({label, kind: 'broadcast'}), label);
            await until('broadcast ' + label, async () =>
                (await Promise.all([pages[0], pages[2]].map(p => p.evaluate(() => received.some(m => m.kind === 'broadcast'))))).every(Boolean));
            await sleep(500);
            assert.deepEqual(await pages[1].evaluate(() => received.filter(m => m.kind === 'direct').map(m => m.seq)), [0,1,2,3,4]);
            for (const p of [pages[0], pages[2]]) assert.equal(await p.evaluate(() => received.filter(m => m.kind === 'direct').length), 0);
            for (const p of pages) assert.deepEqual(await p.evaluate(() => channelErrors), []);
            pass('batch/group session acknowledgements, ordered direct messages and recipient isolation: ' + label);
        }
        await messaging('initial');
        await Promise.all(pages.map((p, i) => p.evaluate(async i => {
            jatos.batchSessionVersioning = false;
            for (let attempt = 0; ; attempt++) {
                try { await jatos.batchSession.set('concurrent' + i, i); break; }
                catch (error) {
                    if (attempt === 4 || !String(error).includes('concurrent')) throw error;
                    await new Promise(r => setTimeout(r, 100 * (i + 1)));
                }
            }
        }, i)));
        await until('concurrent batch convergence', async () =>
            (await Promise.all(pages.map(p => p.evaluate(() => [0,1,2].every(i => jatos.batchSession.get('concurrent' + i) === i))))).every(Boolean));
        pass('concurrent cross-node batch updates converge without lost keys');
        await pages[0].evaluate(() => jatos.uploadResultFile('shared upload Grüße', 'shared.txt'));
        response = await pages[0].context().request.get(worker + `/publix/${members[0].uuid}/files/shared.txt`);
        assert.equal(response.status(), 200);
        assert.equal(await response.text(), 'shared upload Grüße');
        pass('assets and uploaded result files accessible through the other node');
        await Promise.all(pages.map((p, i) => p.evaluate(i => jatos.submitResultData({participant: i, phase: 'before', text: '日本語'}), i)));
        await pages[0].evaluate(() => jatos.groupSession.set('survives', 'restart-marker'));


        async function restart(service, survivor, abrupt = false) {
            console.log('EXERCISE', abrupt ? 'kill' : 'restart', service);
            await composeAsync(...(abrupt ? ['kill', '-s', 'SIGKILL', service] : ['stop', '--timeout', '20', service]));
            const live = await admin.get(survivor + '/ping');
            assert.equal(live.status(), 200);
            await composeAsync('start', service);
            assert.equal(base(service), service === 'jatos' ? worker : seed, 'Published port changed on restart');
            await until('node health ' + service, async () => (await admin.get(base(service) + '/ping', {timeout: 2000})).status() === 200);
            await until('group reconnection ' + service, async () =>
                (await Promise.all(pages.map(p => p.evaluate(() => jatos.groupChannels.length)))).every(n => n === 3));
            await until('batch reconnection ' + service, async () => {
                try { await pages[0].evaluate(() => jatos.batchSession.set('afterRestart', Date.now())); return true; } catch { return false; }
            });
            await messaging((abrupt ? 'killed-' : 'restarted-') + service);
            assert.deepEqual(await Promise.all(pages.map(p => p.evaluate(() => jatos.groupResultId))), groupIds);
            for (const p of pages) {
                assert.equal(await p.evaluate(() => jatos.groupSession.get('survives')), 'restart-marker');
                assert(await p.evaluate(() => [0,1,2].every(i => jatos.batchSession.get('concurrent' + i) === i)));
            }
            // Exercise the proxy after recovery; the diagnostic header records the upstream actually used.
            const upstreams = new Set();
            for (let i = 0; i < 12; i++) {
                const ping = await admin.get(proxy + '/ping');
                assert.equal(ping.status(), 200);
                upstreams.add(ping.headers()['x-jatos-test-node']);
            }
            assert.equal(upstreams.size, 2, 'Proxy must route to both recovered nodes');
            pass('proxy routes to both nodes after ' + service + ' recovery');
        }
        await restart('jatos', seed);
        await restart('jatos-seed', worker);
        await restart('jatos', seed, true);
        response = await pages[0].context().request.get(worker + `/publix/${members[0].uuid}/files/shared.txt`);
        assert.equal(response.status(), 200);
        assert.equal(await response.text(), 'shared upload Grüße');
        for (const [i, page] of pages.entries()) {
            await page.evaluate(async i => {
                await jatos.appendResultData('\n' + JSON.stringify({participant: i, phase: 'after', text: '日本語'}));
                await jatos.leaveGroup();
                await jatos.endStudyAjax(true, 'Multi-node integration');
            }, i);
        }
        await until('group cleanup', async () => {
            const groups = (await (await api(seed + `/jatos/api/v1/batches/${batch.id}/groups`)).json()).data;
            return groups.length === 1 && groups[0].activeMemberList.length === 0;
        });
        for (const origin of [seed, worker]) {
            const metadata = (await (await api(origin + `/jatos/api/v1/results/metadata?studyId=${study.id}`)).json()).data;
            const results = metadata[0].studyResults;
            assert.equal(results.length, 3);
            assert(results.every(r => r.studyState === 'FINISHED'));
        }
        const archive = path.join(process.env.JATOS_IT_OUTPUT, 'results.zip');
        writeFileSync(archive, await (await api(worker + `/jatos/api/v1/results/data?studyId=${study.id}`)).body());
        execFileSync('python3', ['-c', `
import json, sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    records = [archive.read(n).decode().splitlines() for n in archive.namelist() if n.endswith('/data.txt')]
assert len(records) == 3, records
seen = set()
for lines in records:
    assert len(lines) == 2, lines
    before, after = map(json.loads, lines)
    assert before['phase'] == 'before' and after['phase'] == 'after'
    assert before['participant'] == after['participant']
    assert before['text'] == after['text'] == '日本語'
    seen.add(before['participant'])
assert seen == {0, 1, 2}, seen
`, archive]);
        pass('results and uploaded files survive restarts; no active group members remain');
        // Force the first proxy connection onto the seed, then fail over while it remains stopped.
        const proxyCodes = (await (await api(seed + `/jatos/api/v1/studies/${study.id}/studyCodes`, 'POST', {
            data: {type: 'PersonalSingle', amount: 2}
        })).json()).data;
        await composeAsync('stop', '--timeout', '20', 'jatos');
        const proxyContext = await browser.newContext();
        contexts.push(proxyContext);
        const proxyPage = await proxyContext.newPage();
        pages.push(proxyPage);
        await proxyPage.goto(proxy + '/publix/' + proxyCodes[0]);
        await proxyPage.evaluate(() => new Promise(resolve => jatos.onLoad(resolve)));
        const proxyUuid = await proxyPage.evaluate(() => jatos.studyResultUuid);
        const proxyResultId = await proxyPage.evaluate(() => jatos.studyResultId);
        await proxyPage.evaluate(() => jatos.submitResultData('before proxy failover'));
        await composeAsync('start', 'jatos');
        await until('worker available for proxy failover', async () => (await admin.get(worker + '/ping', {timeout: 2000})).status() === 200);
        // HTTP health precedes cluster membership. Prove the survivor can open a real
        // batch channel before taking the other node down (not a simultaneous outage).
        const readinessContext = await browser.newContext();
        contexts.push(readinessContext);
        const readinessPage = await readinessContext.newPage();
        pages.push(readinessPage);
        await readinessPage.goto(worker + '/publix/' + proxyCodes[1]);
        await readinessPage.evaluate(() => new Promise(resolve => jatos.onLoad(resolve)));
        const readinessResultId = await readinessPage.evaluate(() => jatos.studyResultId);
        await until('survivor batch channel and cross-node publication ready', async () => {
            await readinessPage.evaluate(() => jatos.batchSession.set('survivorReady', true));
            return proxyPage.evaluate(() => jatos.batchSession.get('survivorReady') === true);
        });
        await readinessPage.evaluate(() => jatos.endStudyAjax(true, 'Survivor readiness probe'));
        await composeAsync('stop', '--timeout', '20', 'jatos-seed');
        await until('participant reconnects through proxy to surviving worker', async () => {
            try {
                return await proxyPage.evaluate(async () => {
                    await jatos.batchSession.set('proxyFailover', true);
                    return jatos.batchSession.get('proxyFailover') === true;
                });
            } catch { return false; }
        });
        await proxyPage.evaluate(async () => {
            await jatos.appendResultData(' / after proxy failover');
            await jatos.endStudyAjax(true, 'Proxy failover integration');
        });
        const proxyMetadata = (await (await api(worker + `/jatos/api/v1/results/metadata?studyId=${study.id}`)).json()).data;
        const proxyResult = proxyMetadata[0].studyResults.find(r => r.uuid === proxyUuid);
        assert.equal(proxyResult.studyState, 'FINISHED');
        const proxyArchive = path.join(process.env.JATOS_IT_OUTPUT, 'proxy-results.zip');
        writeFileSync(proxyArchive, await (await api(worker + `/jatos/api/v1/results/data?studyId=${study.id}`)).body());
        execFileSync('python3', ['-c', `
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    names = [n for n in archive.namelist() if n.endswith('/comp-result_' + sys.argv[2] + '/data.txt')]
    assert len(names) == 1, names
    assert archive.read(names[0]).decode() == 'before proxy failover / after proxy failover'
`, proxyArchive, String(proxyResult.componentResults[0].id)]);
        await composeAsync('start', 'jatos-seed');
        await until('seed restored after proxy failover', async () => (await admin.get(seed + '/ping', {timeout: 2000})).status() === 200);
        pass('participant reconnects through Nginx and finishes while original node remains stopped');
        await api(seed + `/jatos/api/v1/studies/${study.id}`, 'DELETE');
        for (const origin of [seed, worker]) {
            const missing = await admin.get(origin + `/jatos/api/v1/studies/${study.id}/properties`, {headers: {Authorization: 'Bearer ' + token}});
            assert.equal(missing.status(), 404);
        }
        for (const service of ['jatos-seed', 'jatos']) {
            compose('exec', '-T', service, 'test', '!', '-d', '/opt/jatos_data/study_assets_root/' + study.dirName);
            for (const id of [...members.map(m => m.id), proxyResultId, readinessResultId]) {
                compose('exec', '-T', service, 'test', '!', '-d', '/opt/jatos_data/result_uploads/study-result_' + id);
            }
        }
        pass('study deletion and shared asset cleanup visible on both nodes');
    } finally {
        evidence.participants = await Promise.all(pages.map(async page => {
            try {
                return await page.evaluate(() => ({url: location.href, groupId: jatos.groupResultId,
                    members: jatos.groupMembers, channels: jatos.groupChannels, errors: window.channelErrors}));
            } catch (error) { return {error: error.message}; }
        }));
        for (const context of contexts.reverse()) await (context.close ? context.close() : context.dispose());
        await browser.close();
        writeFileSync(path.join(process.env.JATOS_IT_OUTPUT, 'summary.json'), JSON.stringify(evidence, null, 2));
        clearTimeout(watchdog);
    }
})().catch(error => { console.error(error); clearTimeout(watchdog); process.exitCode = 1; });
