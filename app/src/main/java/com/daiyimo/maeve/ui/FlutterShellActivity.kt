package com.daiyimo.maeve.ui

/**
 * Source compatibility for callers of the former entry component. The manifest
 * declares this name as an alias of MainActivity, so Android always uses the
 * same singleTask Flutter host rather than creating a second engine attachment.
 */
class FlutterShellActivity : MainActivity()
