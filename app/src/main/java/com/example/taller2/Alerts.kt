package com.example.taller2

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

class Alerts(private val context: Context) {
    fun shortSimpleSnackbar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
    }

    fun indefiniteSnackbar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE).show()
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
