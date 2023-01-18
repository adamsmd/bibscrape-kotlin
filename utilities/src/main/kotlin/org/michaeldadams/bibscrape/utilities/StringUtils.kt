/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package org.michaeldadams.bibscrape.utilities

import org.michaeldadams.bibscrape.list.LinkedList

class StringUtils {
    companion object {
        fun join(source: LinkedList): String {
            return JoinUtils.join(source)
        }

        fun split(source: String): LinkedList {
            return SplitUtils.split(source)
        }
    }
}
