package qrmaster.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import qrmaster.web.ui.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Mount into the sized #qm-app container (not <body>) so the shell's
    // Adsterra strip below it stays visible — a body-mounted canvas is
    // sized to the full window and paints over the fixed ad elements.
    ComposeViewport(document.getElementById("qm-app")!!) {
        App()
    }
}
