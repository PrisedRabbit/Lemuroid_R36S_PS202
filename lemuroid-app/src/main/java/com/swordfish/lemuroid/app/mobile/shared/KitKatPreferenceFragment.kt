package com.swordfish.lemuroid.app.mobile.shared

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.swordfish.lemuroid.R

abstract class KitKatPreferenceFragment : PreferenceFragmentCompat() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                bindPreferenceView(view)
            }

            override fun onChildViewDetachedFromWindow(view: View) = Unit
        })

        for (index in 0 until listView.childCount) {
            bindPreferenceView(listView.getChildAt(index))
        }
    }

    private fun bindPreferenceView(view: View) {
        view.setBackgroundResource(R.drawable.item_focus_background)
        view.isActivated = view.hasFocus()
        view.setOnFocusChangeListener { focusedView, hasFocus ->
            focusedView.isActivated = hasFocus
            focusedView.isSelected = hasFocus
        }
    }
}
