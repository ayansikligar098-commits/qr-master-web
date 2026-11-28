package qrmaster.web.ui

import kotlin.JsFun

/**
 * Bridges to the File Share upload engine in index.html
 * (window.__qmFs*). The engine keeps the picked File object in JS and
 * uploads it straight to R2 in parallel parts; Kotlin only polls a
 * small JSON status. See data/FileShare.kt for the JSON API client.
 */

@JsFun("() => { return window.__qmFsPick(); }")
external fun fsPickFile(): Int

/** "" while pending, "CANCELLED", or JSON {ref,name,size,type,lastModified}. */
@JsFun("(id) => { return window.__qmFsPollPick(id); }")
external fun fsPollPick(id: Int): String

@JsFun("(on) => { window.__qmFsDrop(on); }")
external fun fsSetDrop(on: Boolean)

/** "" or JSON {ref,name,size,type,lastModified} for a dropped file (consumed once). */
@JsFun("() => { return window.__qmFsPollDrop(); }")
external fun fsPollDrop(): String

@JsFun("() => { return window.__qmFsDragging(); }")
external fun fsDragging(): Boolean

/** JSON {found, partsDone, partCount} — an earlier interrupted upload of this file. */
@JsFun("(ref) => { return window.__qmFsResumable(ref); }")
external fun fsResumable(ref: Int): String

/** Abort + forget a saved (resumable) session for this file. */
@JsFun("(ref, token) => { window.__qmFsForget(ref, token); }")
external fun fsForgetSaved(ref: Int, token: String)

/** mode: "fresh" | "resume". token: Firebase ID token ("" when none). */
@JsFun("(ref, mode, token, expiryDays, password) => { window.__qmFsStart(ref, mode, token, expiryDays, password); }")
external fun fsStartUpload(ref: Int, mode: String, token: String, expiryDays: Int, password: String)

/** JSON status: state, pct, uploaded, total, speed, eta, parts, shareId, deleteToken, expiresAt, tier, maxBytes, error. */
@JsFun("() => { return window.__qmFsStatus(); }")
external fun fsUploadStatus(): String

@JsFun("() => { window.__qmFsPause(); }")
external fun fsPauseUpload()

@JsFun("() => { window.__qmFsResume(); }")
external fun fsResumeUpload()

@JsFun("(token) => { window.__qmFsCancel(token); }")
external fun fsCancelUpload(token: String)

/** Delete token saved in this browser for a share id ("" when none). */
@JsFun("(shareId) => { return window.__qmFsSavedToken(shareId); }")
external fun fsSavedToken(shareId: String): String
