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
