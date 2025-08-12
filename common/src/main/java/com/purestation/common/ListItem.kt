package com.purestation.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.purestation.common.databinding.ListItemBinding

data class ListItem(
    val title: String,
    val activity: Class<out Activity>
)

fun interface ListItemClickListener {
    fun onClick(item: ListItem)
}

class ListItemAdapter(private val context: Context, private val items: List<ListItem>) : RecyclerView.Adapter<ListItemAdapter.ViewHolder>() {
    inner class ViewHolder(val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root)

    private val clickListener = ListItemClickListener { item ->
        context.startActivity(Intent(context, item.activity))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding: ListItemBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.list_item,
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.item = item
        holder.binding.clickListener = clickListener
    }

    override fun getItemCount() = items.size
}