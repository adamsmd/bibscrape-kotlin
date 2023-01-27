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
