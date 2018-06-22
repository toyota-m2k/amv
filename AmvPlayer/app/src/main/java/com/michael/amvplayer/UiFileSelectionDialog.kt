package com.michael.amvplayer

import android.app.AlertDialog
import android.app.Dialog
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.DialogFragment

class UiFileSelectionDialog : DialogFragment() {
    class FileSelectorViewModel : ViewModel() {
        val dialogOk = MutableLiveData<Unit>()
        val dialogCancel = MutableLiveData<Unit>()
    }
    companion object {
        fun newInstance() = UiFileSelectionDialog()
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        val viewModel = ViewModelProviders.of(activity!!).get(FileSelectorViewModel::class.java)
        builder.setMessage("Hello")
                .setPositiveButton("Yes") { _, _ ->
                    viewModel.dialogOk.value = Unit
                }
                .setNegativeButton("Cancel") { _, _ ->
                    viewModel.dialogCancel.value = Unit
                }
        return builder.create()
    }
}