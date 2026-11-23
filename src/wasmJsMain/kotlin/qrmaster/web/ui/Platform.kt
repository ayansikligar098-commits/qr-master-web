package qrmaster.web.ui

import kotlin.js.JsFun

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
