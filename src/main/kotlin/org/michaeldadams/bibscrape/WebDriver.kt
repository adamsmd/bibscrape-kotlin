package org.michaeldadams.bibscrape

import net.lightbody.bmp.BrowserMobProxyServer
import org.openqa.selenium.By
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxOptions
import org.openqa.selenium.firefox.GeckoDriverService
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.remote.CapabilityType
import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentSkipListSet
import kotlin.math.roundToLong

// @Suppress("ClassOrdering", "WRONG_ORDER_IN_CLASS_LIKE_STRUCTURES")
private const val MILLIS_PER_SECOND = 1_000

/** The `innerHTML` property of a [WebElement]. */
val WebElement.innerHtml: String
  get() = this.getDomProperty("innerHTML")

class Driver private constructor(
  val driver: RemoteWebDriver,
  val proxy: BrowserMobProxyServer) :
  WebDriver by driver, JavascriptExecutor by driver, Closeable {
  var closed = AtomicBoolean()

  override fun close() {
    if (!closed.getAndSet(true)) {
      // TODO: do in another thread?
      Thread.sleep(1_000) // Fixes "Timed out waiting for driver server to stop" (sometimes)
      driver.quit()
      proxy.stop()
    }
  }

  fun finalize() { this.close() }

  /** Sets the driver's wait time while running a given [block].
   *
   * @param timeout the time to wait in seconds
   * @param block the code to execute and wait on
   * @return the value returned by the [block]
   */
  fun <T> await(timeout: Double = 30.0, block: (WebDriver) -> T): T {
    val oldWait = this.manage().timeouts().implicitWaitTimeout
    this.manage().timeouts().implicitlyWait(Duration.ofMillis((MILLIS_PER_SECOND * timeout).roundToLong()))
    val result = block(this)
    this.manage().timeouts().implicitlyWait(oldWait)
    return result
  }

  fun awaitFindElement(by: By): WebElement = this.await { it.findElement(by) }
  fun awaitFindElements(by: By): List<WebElement> = this.await { it.findElements(by) }

  // fun <T> await(driver: WebDriver, block: () -> T?, timeout: Double = 30.0, sleep: Double = 0.5): T {
  //   val start = Clock.System.now()
  //   while (true) {
  //     try {
  //       val result = block()
  //       if (result != null) { return result }
  //     } catch (e: Exception) {}
  //     if ((Clock.System.now() - start) > timeout.seconds) {
  //       throw Error("Timeout while waiting for the browser")
  //     }
  //     Thread.sleep((sleep * 1000.0).roundToLong())
  //   }
  // }
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

  companion object {
    val pids = ConcurrentSkipListSet<Int>()
    val directories = ConcurrentSkipListSet<String>()
    init {
      Runtime.getRuntime().addShutdownHook(Thread {
        for (pid in pids) {
          try {
          // TODO: process kill
          } catch (e: Throwable) {
            // Print stack trace unless exception is that pid not exist
          }
        }
        // for (directory in directories) {
        //   try {
        //     directory.deleteRecursively()
        //   } catch (e: Throwable) {
        //     // Print stack trace unless exception is that pid not exist
        //   }
        // }
      })
    }

    fun make(headless: Boolean, noOutput: Boolean): Driver {
      // Would prefer to use org.openqa.selenium.remote.http.Filter,
      // NetworkInterceptor or devTools.createSession(), but that breaks on
      // Firefox

      val proxy = net.lightbody.bmp.BrowserMobProxyServer()
      proxy.start(0)
      val seleniumProxy = net.lightbody.bmp.client.ClientUtil.createSeleniumProxy(proxy)
      // println("XXX:"+seleniumProxy.getHttpProxy())

      proxy.addResponseFilter( { response, contents, messageInfo ->
        // println("responding: $response\n")
        response.headers().remove("Content-Disposition")
        // null
      })
      // proxy.addRequestFilter({ request, contents, messageInfo ->
      //   if (request.uri.startsWith("https://disqus")) { HttpResponse(404) }
      //   else null
      // })

      val capabilities = DesiredCapabilities()
      capabilities.setCapability(CapabilityType.PROXY, seleniumProxy)

      val options = FirefoxOptions(capabilities)
      options.setProxy(seleniumProxy)
      // val profile = options.profile
      // profile.setPreference("fission.webContentIsolationStrategy", 0 as java.lang.Integer)
      // profile.setPreference("fission.bfcacheInParent", false as java.lang.Boolean)
      // profile.setPreference("foo", java.lang.String("bar"))
      // options.setProfile(profile)
      if (headless) {
        options.addArguments("--headless")
      }
      // TODO: option for withLogFile
      val serviceBuilder = GeckoDriverService.Builder()
      if (noOutput) {
        // Prevent debugging noise
        // serviceBuilder.withLogFile(java.io.File("/dev/null")) // TODO: or "NUL" on windows
      }
      val service = serviceBuilder.build()
      val driver = FirefoxDriver(service, options)
      // #profile.set_preference('browser.download.panel.shown', False)
      // #profile.set_preference('browser.helperApps.neverAsk.openFile',
      // #  'text/plain,text/x-bibtex,application/x-bibtex,application/x-research-info-systems')
      // profile.set_preference('browser.helperApps.neverAsk.saveToDisk',
      //   'application/atom+xml,application/x-bibtex,application/x-research-info-systems,text/plain,text/x-bibtex')
      // profile.set_preference('browser.download.folderList', 2) # Use a custom folder for downloading
      // profile.set_preference('browser.download.dir', '$downloads')
      // #profile.set_preference('permissions.default.image', 2) # Never load the images
      // val downloadDirectory = kotlin.io.path.createTempDirectory()
      // Runtime.getRuntime().addShutdownHook(Thread { downloadDirectory.deleteRecursively() })
      return Driver(driver, proxy)
    }
  }
}
