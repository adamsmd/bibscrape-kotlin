package org.michaeldadams.bibscrape

import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import net.lightbody.bmp.BrowserMobProxyServer
import net.lightbody.bmp.client.ClientUtil
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
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
import java.time.Duration
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong

// @Suppress("ClassOrdering", "WRONG_ORDER_IN_CLASS_LIKE_STRUCTURES")
private const val MILLIS_PER_SECOND = 1_000

/** The `innerHTML` property of a [WebElement]. */
val WebElement.innerHtml: String
  get() = this.getDomProperty("innerHTML")

class Driver private constructor(
  val driver: RemoteWebDriver,
  val proxy: BrowserMobProxyServer
) : WebDriver by driver, JavascriptExecutor by driver, Closeable {
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
   * @param T the type returned by [block]
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
    // TODO: weak table of drivers
    val pids = ConcurrentSkipListSet<Int>()
    val directories = ConcurrentSkipListSet<String>()
    init {
      Runtime.getRuntime().addShutdownHook(
        Thread {
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
        }
      )
    }

    fun make(headless: Boolean, verbose: Boolean, timeout: Double): Driver {
      // // Proxy
      // Would prefer to use org.openqa.selenium.remote.http.Filter,
      // NetworkInterceptor or devTools.createSession(), but all of those break
      // on Firefox
      val proxy = BrowserMobProxyServer()
      proxy.addResponseFilter { response, /*contents*/ _, /*messageInfo*/ _ ->
        response.headers().remove("Content-Disposition")
      }
      proxy.addRequestFilter { request, /*contents*/ _, /*messageInfo*/ _ ->
        // disqus.com is sometimes slow to respond, and we don't need it, so we block it
        // if (request.uri().contains("disqus")) {
        //   println()
        //   println("DISQUS SIMPLE:" + request)
        //   println()
        // }
        // if (request.headers()[HttpHeaderNames.HOST].contains("\\.disqus\\.com:".r)) {
        //   println("DISQUS")
        //   DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
        // } else {
        //   null
        // }
        null
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
        // Prevent debugging noise
        val nullFile = if (File.separatorChar == '\\') "NUL" else "/dev/null"
        serviceBuilder.withLogFile(File(nullFile))
      }
      val service = serviceBuilder.build()

      // // Firefox Driver
      val driver = FirefoxDriver(service, options)
      // TODO: fix timeouts
      // driver.manage().timeouts().implicitlyWait(
      //   Duration.ofMillis((timeout * MILLIS_PER_SECOND).roundToLong()))

      // // Result
      return Driver(driver, proxy)
    }
  }
}
