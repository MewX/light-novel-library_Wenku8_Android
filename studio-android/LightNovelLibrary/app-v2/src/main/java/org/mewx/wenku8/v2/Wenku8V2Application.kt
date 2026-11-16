package org.mewx.wenku8.v2

import android.app.Application

/**
 * Application entry point for Wenku8 `:app-v2`.
 *
 * Keeps cold-start `onCreate` lightweight (zero blocking I/O or third-party SDK initialization on
 * the main thread). Room WAL database, DataStore preferences, and legacy `v1.31` migrator graph
 * attach lazily in Phase `P0.2` / `P0.3`.
 */
class Wenku8V2Application : Application()
