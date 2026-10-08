import {applyPatch} from "fast-json-patch/module/core.mjs";

/**
 * Owns local session data and its applied version, ordering and gap recovery.
 * Receives normalized updates; requestFullSession supplies the channel-specific transport.
 */
export function createSessionSync({requestFullSession, getTimeout}) {
    let data = {};
    let version;
    const getVersion = () => version;

    function apply({update, onApplied}) {
        if (update.patches !== undefined || update.fullSession !== undefined) {
            data = applySessionUpdate(data, update.patches, update.fullSession);
            if (update.version !== undefined) version = update.version;
        }
        onApplied();
    }
    const buffered = new Map();
    const waiters = new Set();
    let target = -1;
    let timer;

    function fail(error) {
        clearTimeout(timer);
        timer = undefined;
        buffered.clear();
        target = -1;
        for (const waiter of [...waiters]) {
            waiters.delete(waiter);
            waiter.done(error);
        }
    }

    function check() {
        if (target < 0 || (getVersion() != null && getVersion() >= target)) {
            clearTimeout(timer);
            timer = undefined;
            for (const waiter of [...waiters]) {
                waiters.delete(waiter);
                waiter.done();
            }
        } else if (timer === undefined) {
            // Allow broadcasts already in flight to fill the gap before requesting full state.
            timer = setTimeout(() => {
                timer = undefined;
                try {
                    timer = setTimeout(() => fail("Timeout synchronizing session"), getTimeout());
                    requestFullSession();
                } catch (error) {
                    fail(error);
                }
            }, Math.min(250, getTimeout()));
        }
    }

    function receive(update, onApplied = () => {}) {
        const message = {update, onApplied};
        const version = update.version;
        const hasVersion = Number.isSafeInteger(version) && version >= 0;
        if (hasVersion && update.fullSession !== undefined) {
            if (getVersion() == null || version >= getVersion()) apply(message);
        } else if (hasVersion && update.patches !== undefined) {
            if (getVersion() != null && version === getVersion() + 1) apply(message);
            else if (getVersion() == null || version > getVersion()) {
                // Bound memory use; a full session also recovers patches beyond this buffer.
                if (buffered.size < 100) buffered.set(version, message);
            }
        } else {
            apply(message);
        }
        if (hasVersion) target = Math.max(target, version);
        for (const version of buffered.keys()) {
            if (version <= getVersion()) buffered.delete(version);
        }
        while (getVersion() != null && buffered.has(getVersion() + 1)) {
            const next = getVersion() + 1;
            const message = buffered.get(next);
            buffered.delete(next);
            apply(message);
        }
        check();
    }

    return {
        getData: () => data,
        getVersion,
        receive,
        observeVersion(version) {
            if (!Number.isSafeInteger(version) || version < 0) return;
            target = Math.max(target, version);
            check();
        },
        waitFor(version, done) {
            target = Math.max(target, version);
            waiters.add({done});
            check();
        },
        cancel() { fail("Session channel closed while synchronizing"); },
        reset() {
            data = {};
            version = null;
            fail("Session channel closed while synchronizing");
        }
    };
}

/**
 * Applies incoming patches before an optional full session. A null full session
 * clears the session; a root patch retains JSON Patch's replacement document.
 */
function applySessionUpdate(data, patches, fullSession) {
    if (patches !== undefined) {
        const results = applyPatch(data, patches);
        if (results && results.newDocument !== undefined) {
            data = results.newDocument;
        }
    }
    if (fullSession !== undefined) {
        data = fullSession === null ? {} : fullSession;
    }
    return data;
}
