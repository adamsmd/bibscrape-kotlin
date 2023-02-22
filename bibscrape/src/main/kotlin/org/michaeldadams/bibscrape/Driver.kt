package org.michaeldadams.bibscrape

import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import net.lightbody.bmp.BrowserMobProxyServer
import net.lightbody.bmp.client.ClientUtil
import org.jsoup.nodes.Entities
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.GeckoDriverService
import org.openqa.selenium.remote.CapabilityType
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/** The `innerHTML` property of a [WebElement]. */
val WebElement.innerHtml: String
  get() = this.getDomProperty("innerHTML")

/** Wrapper around [WebDriver] providing some extra functionality.
 *
 * @property driver The [WebDriver] being wrapped.
 * @property proxy The proxy used to modify traffic to the [driver].
 */
class Driver private constructor(
  val driver: RemoteWebDriver,
  val proxy: BrowserMobProxyServer
) :
  Object(),
  WebDriver by driver,
  JavascriptExecutor by driver,
  Closeable {
  /** Whether this object has already been closed. */
  private var closed = AtomicBoolean()

  override fun close(): Unit {
    if (!closed.getAndSet(true)) {
      // TODO: do in another thread?
      Thread.sleep(1.seconds.inWholeMilliseconds) // Fixes "Timed out waiting for driver server to stop" (sometimes)
      driver.quit()
      proxy.stop()
    }
  }

  /** Calls [close] when this object is garbage collected in case the user did
   * not already do so. */
  protected override fun finalize(): Unit = this.close()

  /** Sets the driver's wait time while running a given [block].
   *
   * @param T the type returned by [block]
   * @param timeout the time to wait in seconds
   * @param block the code to execute and wait on
   * @return the value returned by the [block]
   */
  fun <T> await(timeout: Duration = 30.0.seconds, block: (WebDriver) -> T): T {
    val oldWait = this.manage().timeouts().implicitWaitTimeout
    this.manage().timeouts().implicitlyWait(timeout.toJavaDuration())
    val result = block(this)
    this.manage().timeouts().implicitlyWait(oldWait)
    return result
  }

  /** Calls [findElement] in an [await] block.
   *
   * @param by TODO: document
   * @return TODO: document
   */
  fun awaitFindElement(by: By): WebElement = this.await { it.findElement(by) }

  /** Calls [findElements] in an [await] block.
   *
   * @param by TODO: document
   * @return TODO: document
   */
  fun awaitFindElements(by: By): List<WebElement> = this.await { it.findElements(by) }

  fun <T> awaitNonNull(timeout: Duration = 30.0.seconds, sleep: Duration = 0.5.seconds, block: () -> T?): T {
    // TODO: selenium.Wait
    val end = Instant.now().plus(timeout.toJavaDuration())
    while (true) {
      runCatching { block()?.let { return it } }
      if (Instant.now().isAfter(end)) {
        throw TimeoutException()
      }
      Thread.sleep(sleep.inWholeMilliseconds)
    }
  }
  // sub await(&block --> Any:D) is export {
  //   my Rat:D constant $timeout = 30.0;
  //   my Rat:D constant $sleep = 0.5;
  //   my Any:_ $result;
  //   my Num:D $start = now.Num;
  //   while True {
  //     $result = &block();
  //     if $result { return $result }
  //     if now - $start > $timeout {
  //       die "Timeout while waiting for the browser"
  //     }
  //     sleep $sleep;
  //     CATCH { default { sleep $sleep; } }
  //   }
  // }

  fun textPlain(): String = Entities.unescape(this.findElement(By.tagName("pre")).innerHtml)

  companion object {
    /** The process of launched drivers. */
    private val pids: ConcurrentSkipListSet<Int> = ConcurrentSkipListSet() // TODO: weak table of drivers

    /** The temporary directories of launched drivers. */
    private val directories: ConcurrentSkipListSet<String> = ConcurrentSkipListSet()

    // init {
    //   Runtime.getRuntime().addShutdownHook(
    //     Thread {
    //       for (pid in pids) {
    //         // runCatching { ... }.getOrNull() // Intentionally ignore if fails
    //         try {
    //           // TODO: process kill
    //         } catch (e: Throwable) {
    //           // Print stack trace unless exception is that pid not exist
    //         }
    //       }
    //       // for (directory in directories) {
    //       //   try {
    //       //     directory.deleteRecursively()
    //       //   } catch (e: Throwable) {
    //       //     // Print stack trace unless exception is that pid not exist
    //       //   }
    //       // }
    //     }
    //   )
    // }

    /** Creates a [Driver].
     *
     * @param headless whether to run the driver in headless mode (i.e., without a visible window)
     * @param verbose whether to let the driver print debug output to stderr
     * @param timeout TODO: not implemented
     * @return the [Driver] object that is created
     **/
    fun make(headless: Boolean, verbose: Boolean, timeout: Duration): Driver {
      // // Proxy
      // Would prefer to use org.openqa.selenium.remote.http.Filter,
      // NetworkInterceptor or devTools.createSession(), but all of those break
      // on Firefox
      val proxy = BrowserMobProxyServer()
      proxy.addResponseFilter {
          response, /*contents*/ _, /*messageInfo*/ _ -> // ktlint-disable experimental:comment-wrapping
        val textPlainTypes = """
          ^ (
            application/atom\+xml |
            application/x-bibtex |
            application/x-research-info-systems |
            text/x-bibtex
            ) (?= $ | ; )
        """.trimIndent().r

        response.headers().remove("Content-Disposition")
        response.headers()["Content-Type"] =
          response.headers()["Content-Type"].replace(textPlainTypes, "text/plain")
      }
      proxy.addRequestFilter {
          request, /*contents*/ _, /*messageInfo*/ _ -> // ktlint-disable experimental:comment-wrapping
        val blockedDomains = """
          (^ | \.) (
            addthis\.com |
            addthisedge\.com |
            ads-twitter\.com |
            airbrake\.io |
            disqus\.com |
            doubleclick\.net |
            firefox\.com |
            google-analytics\.com |
            googlesyndication\.com |
            googletagmanager\.com |
            heapanalytics\.com |
            hotjar\.com |
            jwplayer\.com |
            linkedin\.com |
            moatads\.com |
            mopinion\.com |
            mozilla\.com |
            mozilla\.net |
            oribi\.io |
            qualtrics\.com |
            scholar\.google\.com |
            site24x7rum\.eu |
            t\.co |
            trendmd\.com |
            videodelivery\.net
          ) $
        """.trimIndent().r

        // Use dummy values for domains that are slow and that we don't actually need
        val domain = request.headers()[HttpHeaderNames.HOST].remove(":443 $".r)
        if (domain.contains(blockedDomains)) {
          DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        } else {
          null
        }
      }
      proxy.start()

      // // Capabilities
      val capabilities = DesiredCapabilities()
      // Could also do: options.setProxy(ClientUtil.createSeleniumProxy(proxy))
      capabilities.setCapability(CapabilityType.PROXY, ClientUtil.createSeleniumProxy(proxy))

      // // Options
      val options = FirefoxOptions(capabilities)

      val profile = options.profile

      // Prevent "Invalid browser preferences for CDP" error
      profile.setPreference("fission.webContentIsolationStrategy", 0)
      profile.setPreference("fission.bfcacheInParent", false)

      options.profile = profile

      if (headless) {
        options.addArguments("--headless")
      }

      // // Service
      val serviceBuilder = GeckoDriverService.Builder()
      if (!verbose) {
        // TODO: pipe the output to an OutputStream or something
        // Prevent debugging noise
        val nullFile = if (File.separatorChar == '\\') "NUL" else "/dev/null"
        serviceBuilder.withLogFile(File(nullFile))
      }
      val service = serviceBuilder.build()

      // // Firefox Driver
      val driver = FirefoxDriver(service, options)
      // TODO: fix timeouts
      // driver.manage().timeouts().implicitlyWait(timeout.toJavaDuration())

      // // Result
      return Driver(driver, proxy)
    }
  }
}
