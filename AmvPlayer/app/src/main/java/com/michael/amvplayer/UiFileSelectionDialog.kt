package com.michael.amvplayer

import android.app.AlertDialog
import android.app.Dialog
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.DialogFragment

class UiFileSelectionDialog : DialogFragment() {
    enum class DlgState {
        INIT, OK, CANCELED
    }
    class FileSelectorViewModel : ViewModel() {
        val state = MutableLiveData<DlgState>();
    }
    companion object {
        fun newInstance() = UiFileSelectionDialog()
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        val viewModel = ViewModelProviders.of(activity!!).get(FileSelectorViewModel::class.java)
        viewModel.state.value = DlgState.INIT;
        builder.setMessage("Hello")
                .setPositiveButton("Yes") { _, _ ->
                    viewModel.state.value = DlgState.OK;
                }
                .setNegativeButton("Cancel") { _, _ ->
                    viewModel.state.value = DlgState.CANCELED;
                }
        return builder.create()
    }
}