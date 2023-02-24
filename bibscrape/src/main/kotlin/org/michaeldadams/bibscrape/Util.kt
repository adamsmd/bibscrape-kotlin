/** Functions for which there is not another good place to put them.
 */
package org.michaeldadams.bibscrape

fun <A> List<A>.nonEmpty(): List<A>? = this.ifEmpty { null } // TODO: place for nonEmpty()

// TODO: put [where] somewhere appropriate
// TODO: rename to ifOrNull
/** When [test] is true, returns the result of calling [block], otherwise returns [null].
 *
 * @param A the type to be returned
 * @param test the test to determine whether to call [block] or just return [null]
 * @param block the block to run if [test] is [true]
 * @return either [null] or the result of calling [block]
 */
inline fun <A> ifOrNull(test: Boolean, block: () -> A): A? = if (test) block() else null
