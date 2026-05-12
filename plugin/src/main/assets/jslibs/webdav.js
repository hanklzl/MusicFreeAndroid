// Minimal `require('webdav')` shim.
//
// Only `createClient(baseUrl, options)` is exported, returning an object with
// `getFileContents(path)` and `putFileContents(path, data)`. Other methods
// (PROPFIND / MKCOL / DELETE / MOVE / etc.) are intentionally absent — the
// matching Kotlin backend in `WebDavShim.kt` does not expose them, so plugins
// that rely on them will throw at call time (the explicit failure we want).
//
// `auth` is { username, password } | null:
//   - if `options.username` is set we pass through the credentials,
//   - otherwise the request is made unauthenticated.
//
// Both methods return a Promise<string> via the underlying `__webdav_*`
// async bridge functions registered by `WebDavShim.register`.
module.exports = {
    createClient: function (baseUrl, options) {
        var auth = null;
        if (options && typeof options.username !== 'undefined') {
            auth = {
                username: String(options.username),
                password: options.password == null ? '' : String(options.password)
            };
        }
        return {
            getFileContents: function (path) {
                return __webdav_get(baseUrl, path, auth);
            },
            putFileContents: function (path, data) {
                var payload = data == null ? '' : (typeof data === 'string' ? data : String(data));
                return __webdav_put(baseUrl, path, payload, auth);
            }
        };
    }
};
