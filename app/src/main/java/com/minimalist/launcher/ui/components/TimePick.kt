package com.minimalist.launcher.ui.components

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat

/** The system time picker in its dark style; [initial] and the result are minutes of the day. */
fun pickTime(context: Context, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        android.R.style.Theme_DeviceDefault_Dialog_Alert,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initial / 60,
        initial % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}
