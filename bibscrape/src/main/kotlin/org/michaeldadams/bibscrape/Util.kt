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

/** Returns null of the receiver is empty or the single element, or throws an
 * exception if the list has more than one element.
 *
 * @receiver the list that is checked
 * @param A the type of elements in the list
 * @return null or the single element from the list
 */
fun <A> List<A>.emptyOrSingle(): A? = this.ifEmpty { null }?.single()

/** Runs [block] until [test] returns true or [times] times have been tried.
 *
 * @param A the type of the result
 * @param times maximum number of calls to [block]
 * @param predicate whether to return a particular result from [block]
 * @param start where to start counting the attempts from
 * @param block the code to repeatedly try running
 * @returns the result of the last call to [block]
 */
fun <A> retry(times: Int, predicate: (A) -> Boolean, start: Int = 1, block: (Int) -> A): A =
  block(start).let { if (predicate(it) || start == times) it else retry(times, predicate, start + 1, block) }

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
