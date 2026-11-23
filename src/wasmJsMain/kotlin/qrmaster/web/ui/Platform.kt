package qrmaster.web.ui

import kotlin.JsFun

/**
 * Tiny browser bridges. The application itself is 100% Kotlin/Compose;
 * these one-line platform hooks only trigger the browser's file download,
 * which Compose cannot do on its own.
 */
@JsFun(
    """(name, b64) => {
        const bin = atob(b64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        const blob = new Blob([bytes], { type: 'image/png' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 3000);
    }"""
)
external fun downloadPngBase64(name: String, base64: String)

@JsFun("() => { window.print(); }")
external fun printPage()

@JsFun(
    """(text) => {
        if (navigator.share) { navigator.share({ text: text }).catch(() => {}); return 'share'; }
        if (navigator.clipboard) { navigator.clipboard.writeText(text).catch(() => {}); return 'copy'; }
        return 'none';
    }"""
)
external fun shareText(text: String): String

@JsFun("(url) => { window.open(url, '_blank'); }")
external fun openExternal(url: String)

/**
 * Starts a real browser download of a remote file (the Android APK).
 * An anchor with a `download` attribute is clicked; the release host
 * answers with Content-Disposition: attachment, so the browser saves
 * the file instead of navigating away from the site.
 */
@JsFun(
    """(url, name) => {
        const a = document.createElement('a');
        a.href = url;
        if (name) a.download = name;
        a.target = '_blank';
        a.rel = 'noopener';
        document.body.appendChild(a);
        a.click();
        a.remove();
    }"""
)
external fun downloadUrl(url: String, name: String)

/** Opens a native file picker; result is polled via [pollPickedFile]. Returns a pick id. */
@JsFun(
    """(accept, withData) => {
        window.__qmPickSeq = (window.__qmPickSeq || 0) + 1;
        const id = window.__qmPickSeq;
        window.__qmPicks = window.__qmPicks || {};
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = accept;
        input.onchange = () => {
            const f = input.files && input.files[0];
            if (!f) { window.__qmPicks[id] = { name: '', size: 0, data: '' }; return; }
            if (withData) {
                const r = new FileReader();
                r.onload = () => { window.__qmPicks[id] = { name: f.name, size: f.size, data: String(r.result || '') }; };
                r.onerror = () => { window.__qmPicks[id] = { name: f.name, size: f.size, data: '' }; };
                r.readAsDataURL(f);
            } else {
                window.__qmPicks[id] = { name: f.name, size: f.size, data: '' };
            }
        };
        document.body.appendChild(input);
        input.click();
        setTimeout(() => input.remove(), 60000);
        return id;
    }"""
)
external fun pickFile(accept: String, withData: Boolean): Int

/** Returns "" while pending, "CANCELLED" if no file, otherwise "name|size|dataUrl". */
@JsFun(
    """(id) => {
        const p = (window.__qmPicks || {})[id];
        if (!p) return '';
        if (!p.name) return 'CANCELLED';
        return p.name + '|' + p.size + '|' + (p.data || '');
    }"""
)
external fun pollPickedFile(id: Int): String

/**
 * Google sign-in bridge. Uses the Firebase JS SDK (loaded in index.html)
 * ONLY for the Google popup flow; the returned identity is handed back as
 * JSON and the rest of the session is handled by the REST layer, exactly
 * like email/password sign-in. The popup opens synchronously inside the
 * click handler, so browser popup blockers treat it as user-initiated.
 */
@JsFun(
    """
(apiKey, authDomain, projectId) => {
  window.__qmGoogle = JSON.stringify({ state: 'pending' });
  try {
    if (typeof firebase === 'undefined') {
      window.__qmGoogle = JSON.stringify({ state: 'error', message: 'Google sign-in is unavailable right now. Check your connection and try again.' });
      return;
    }
    if (!window.__qmFbReady) {
      firebase.initializeApp({ apiKey: apiKey, authDomain: authDomain, projectId: projectId });
      window.__qmFbReady = true;
    }
    var provider = new firebase.auth.GoogleAuthProvider();
    firebase.auth().signInWithPopup(provider).then(function(result) {
      var user = result.user;
      return user.getIdToken().then(function(tok) {
        window.__qmGoogle = JSON.stringify({ state: 'ok', uid: user.uid, email: user.email || '', name: user.displayName || '', idToken: tok, refreshToken: user.refreshToken || '' });
      });
    }).catch(function(e) {
      window.__qmGoogle = JSON.stringify({ state: 'error', message: (e && e.message) ? e.message : 'Google sign-in failed.', code: (e && e.code) ? e.code : '' });
    });
  } catch (e) {
    window.__qmGoogle = JSON.stringify({ state: 'error', message: '' + e });
  }
}
"""
)
external fun startGoogleSignIn(apiKey: String, authDomain: String, projectId: String)

@JsFun("() => { return window.__qmGoogle || ''; }")
external fun pollGoogleSignIn(): String
