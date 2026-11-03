package sh.aminov.golda

import kotlinx.coroutines.runBlocking

/** Runs Kotest property checks inside a plain JUnit test, which must return nothing. */
fun prop(block: suspend () -> Unit) {
    runBlocking { block() }
}
