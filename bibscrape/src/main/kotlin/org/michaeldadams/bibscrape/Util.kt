/** Functions for which there is not another good place to put them. */

package org.michaeldadams.bibscrape

/** When [test] is true, returns the result of calling [block], otherwise returns [null].
 *
 * @param A the type to be returned
 * @param test the test to determine whether to call [block] or just return [null]
 * @param block the block to run if [test] is [true]
 * @return either [null] or the result of calling [block]
 */
inline fun <A> ifOrNull(test: Boolean, block: () -> A): A? = if (test) block() else null

fun <A> List<A>.emptyOrSingle(): A? = this.ifEmpty { null }?.single()

/** Repeatedly applies [block] to [init] until a fixed point is reached.
 *
 * @param A the type that is iterated over
 * @param init the initial value from which to start the calculation
 * @param block the operation to repeatedly apply
 * @return the first value for which [block] returns the same value as its argument
 */
inline fun <A> fixedpoint(init: A, block: (A) -> A): A {
  var oldValue: A? = null
  var newValue: A = init
  while (oldValue != newValue) {
    oldValue = newValue
    newValue = block(newValue)
  }
  return newValue
}
