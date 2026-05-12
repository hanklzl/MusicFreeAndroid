// Minimal whatwg-URL polyfill for QuickJS-kt.
//
// QuickJS itself does NOT ship the `URL` constructor — it's not part of the
// ECMAScript spec, only the WHATWG URL spec. RN MusicFree plugins use
// `new URL(...)` for parsing, so we inject this lightweight implementation
// at engine bootstrap time.
//
// This polyfill covers the subset that the plugin ecosystem actually exercises:
//   protocol, username, password, host, hostname, port, pathname, search, hash,
//   origin, href, toString().
//
// It does NOT implement: URLSearchParams, percent-encoding round-trip,
// IDNA / punycode, base URL resolution. If a plugin needs those it will fail
// at runtime — which is the explicit failure we want (vs. silent corruption).
//
// Self-installation idempotency: bails out if `URL` is already defined (so a
// future QuickJS upgrade that adds the constructor natively can transparently
// replace this).
(function (global) {
    if (typeof global.URL === 'function') {
        // Smoke-probe: a real implementation accepts `new URL(string)`. If the
        // existing global throws on construction we still need to install the
        // polyfill. The probe runs once at boot, not per call.
        try {
            new global.URL('https://example.com/');
            return;
        } catch (_e) {
            // fall through to install our polyfill
        }
    }

    var SCHEME_RE = /^([a-z][a-z0-9+.\-]*):\/\/([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/i;

    function URL(input) {
        if (input == null) {
            throw new TypeError('Invalid URL: ' + input);
        }
        var text = String(input);
        var match = text.match(SCHEME_RE);
        if (!match) {
            throw new TypeError('Invalid URL: ' + text);
        }
        var schemeRaw = match[1];
        var authority = match[2] || '';
        var pathPart = match[3];
        var searchPart = match[4];
        var hashPart = match[5];

        this.protocol = schemeRaw.toLowerCase() + ':';

        // authority = [userinfo "@"] host [":" port]
        var atIdx = authority.lastIndexOf('@');
        if (atIdx >= 0) {
            var userinfo = authority.slice(0, atIdx);
            var hostPort = authority.slice(atIdx + 1);
            var colonInUser = userinfo.indexOf(':');
            if (colonInUser >= 0) {
                this.username = userinfo.slice(0, colonInUser);
                this.password = userinfo.slice(colonInUser + 1);
            } else {
                this.username = userinfo;
                this.password = '';
            }
            this.host = hostPort;
        } else {
            this.username = '';
            this.password = '';
            this.host = authority;
        }

        var hostColon = this.host.lastIndexOf(':');
        // Skip IPv6 bracket case where the colon is inside `[...]`.
        var hostBracket = this.host.lastIndexOf(']');
        if (hostColon >= 0 && hostColon > hostBracket) {
            this.hostname = this.host.slice(0, hostColon);
            this.port = this.host.slice(hostColon + 1);
        } else {
            this.hostname = this.host;
            this.port = '';
        }

        // pathname defaults to "/" when authority is present but path is empty
        // (matches whatwg behavior for hierarchical schemes).
        this.pathname = pathPart && pathPart.length > 0 ? pathPart : '/';
        this.search = searchPart || '';
        this.hash = hashPart || '';
        this.origin = this.protocol + '//' + this.host;

        // `href` is the round-trip serialization; we mirror whatwg by stripping
        // the userinfo from it (callers that need credentials use username /
        // password directly).
        var hrefHost = this.host;
        this.href = this.protocol + '//' + hrefHost + this.pathname + this.search + this.hash;
    }

    URL.prototype.toString = function () {
        return this.href;
    };

    global.URL = URL;
})(globalThis);
