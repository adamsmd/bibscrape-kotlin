/** Extension functions for [WebDriver] */
package org.michaeldadams.bibscrape.webdriverextensions

import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import java.time.Duration
import kotlin.math.roundToLong

/** The `innerHTML` property of a [WebElement]. */
val WebElement.innerHtml: String
  get() = this.getDomProperty("innerHTML")

/** Executes a given [script].
 *
 * @param script the JavaScript code to run
 * @param args the arguments to pass to the [script]
 * @return the value returned by the [script]
*/
fun WebDriver.executeScript(script: String, vararg args: Any?): Any? =
  (this as JavascriptExecutor).executeScript(script, *args)

// @Suppress("ClassOrdering", "WRONG_ORDER_IN_CLASS_LIKE_STRUCTURES")
private const val MILLIS_PER_SECOND = 1_000

/** Sets the driver's wait time while running a given [block].
 *
 * @param timeout the time to wait in seconds
 * @param block the code to execute and wait on
 * @return the value returned by the [block]
 */
fun <T> WebDriver.await(timeout: Double = 30.0, block: (WebDriver) -> T): T {
  val oldWait = this.manage().timeouts().implicitWaitTimeout
  this.manage().timeouts().implicitlyWait(Duration.ofMillis((MILLIS_PER_SECOND * timeout).roundToLong()))
  val result = block(this)
  this.manage().timeouts().implicitlyWait(oldWait)
  return result
}

fun WebDriver.awaitFindElement(by: By): WebElement = this.await { it.findElement(by) }
fun WebDriver.awaitFindElements(by: By): List<WebElement> = this.await { it.findElements(by) }

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
