package ru.mdview.ui

import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRendering
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import ru.mdview.core.Theme
import java.net.URI
import java.nio.file.Path
import java.util.logging.Logger

/**
 * Общий браузерный слой: один CefClient на все вкладки плюс мост «страница → приложение».
 *
 * Клиент один, потому что обработчики приходят с указанием конкретного браузера —
 * отдельный клиент на вкладку означал бы десяток копий одних и тех же обработчиков
 * и больше возни при закрытии.
 */
class BrowserWorkspace(
    private val actions: PageActions,
) {

    private val app: CefApp = CefEngine.start()
    private val client: CefClient = app.createClient()

    private val router: CefMessageRouter = CefMessageRouter.create(
        CefMessageRouter.CefMessageRouterConfig(JS_QUERY_FUNCTION, JS_CANCEL_FUNCTION),
    )

    init {
        router.addHandler(QueryHandler(), true)
        client.addMessageRouter(router)
        client.addRequestHandler(NavigationGuard())
        client.addLifeSpanHandler(PopupGuard())
        client.addLoadHandler(LoadLogger())
    }

    fun createBrowser(startUrl: String): CefBrowser =
        client.createBrowser(startUrl, CefRendering.DEFAULT, false)

    fun dispose() {
        runCatching { router.dispose() }
        runCatching { client.dispose() }
        CefEngine.shutdown()
    }

    /** Мост из javascript: окно mdviewQuery({request, onSuccess, onFailure}) из mdview.js. */
    private inner class QueryHandler : CefMessageRouterHandlerAdapter() {

        override fun onQuery(
            browser: CefBrowser,
            frame: CefFrame,
            queryId: Long,
            request: String,
            persistent: Boolean,
            callback: CefQueryCallback,
        ): Boolean {
            val (command, payload) = request.substringBefore(':', request) to request.substringAfter(':', "")

            return try {
                when (command) {
                    "open" -> actions.openDocument(Path.of(payload))
                    "reload" -> actions.reloadCurrent()
                    "edit" -> actions.openInEditor(payload)
                    "browser" -> actions.openInExternalBrowser()
                    "theme" -> actions.persistTheme(Theme.fromId(payload))
                    "scale" -> actions.persistFontScale(payload.toDoubleOrNull() ?: 1.0)
                    "toc" -> actions.persistShowToc(payload == "on")
                    "copy" -> actions.copyToClipboard(java.net.URLDecoder.decode(payload, "UTF-8"))
                    else -> return false
                }
                callback.success("ok")
                true
            } catch (e: RuntimeException) {
                log.warning("Запрос страницы '$request' не выполнен: ${e.message}")
                callback.failure(1, e.message ?: "ошибка")
                true
            }
        }
    }

    /**
     * Перехват переходов.
     *
     * Ссылка на соседний markdown открывается у нас же новой вкладкой, а не уводит
     * страницу в браузер. Внешние ссылки уходят в системный браузер: просмотрщик
     * предназначен для документов, а не для серфинга.
     */
    private inner class NavigationGuard : CefRequestHandlerAdapter() {

        override fun onBeforeBrowse(
            browser: CefBrowser,
            frame: CefFrame,
            request: CefRequest,
            userGesture: Boolean,
            isMainFrame: Boolean,
        ): Boolean {
            val url = request.getURL()

            when {
                url.isBlank() || url.startsWith("about:") -> return false
                isDocumentUrl(url) -> {
                    actions.openDocument(toPath(url))
                    return true
                }
                !isLocalFile(url) -> {
                    if (isMainFrame) actions.openExternalUrl(url)
                    return true
                }
                else -> return false
            }
        }
    }

    /** target=_blank и window.open: нового окна браузера не заводим, ссылку отдаём наружу. */
    private inner class PopupGuard : CefLifeSpanHandlerAdapter() {

        override fun onBeforePopup(
            browser: CefBrowser,
            frame: CefFrame,
            targetUrl: String,
            targetFrameName: String,
        ): Boolean {
            when {
                isDocumentUrl(targetUrl) -> actions.openDocument(toPath(targetUrl))
                else -> actions.openExternalUrl(targetUrl)
            }
            return true
        }
    }

    private inner class LoadLogger : CefLoadHandlerAdapter() {
        override fun onLoadError(
            browser: CefBrowser,
            frame: CefFrame,
            errorCode: CefLoadHandler.ErrorCode,
            errorText: String,
            failedUrl: String,
        ) {
            log.warning("Страница не загрузилась ($errorCode $errorText): $failedUrl")
        }
    }

    private fun isDocumentUrl(url: String): Boolean =
        isLocalFile(url) && DOCUMENT_EXTENSIONS.any { url.endsWith(it, ignoreCase = true) }

    private fun isLocalFile(url: String): Boolean = url.startsWith("file:")

    private fun toPath(url: String): Path = Path.of(URI(url))

    private companion object {
        const val JS_QUERY_FUNCTION = "mdviewQuery"
        const val JS_CANCEL_FUNCTION = "mdviewCancel"

        val DOCUMENT_EXTENSIONS = listOf(".md", ".markdown", ".mdown", ".mkd")
        val log = Logger.getLogger(BrowserWorkspace::class.java.name)
    }
}
