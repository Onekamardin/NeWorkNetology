package ru.netology.nework.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nework.data.model.Event
import ru.netology.nework.databinding.ItemEventBinding
import ru.netology.nework.R
import ru.netology.nework.ui.formatServerDate

class EventAdapter : ListAdapter<Event, EventAdapter.EventHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EventHolder(
        ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: EventHolder, position: Int) = holder.bind(getItem(position))

    class EventHolder(private val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            binding.author.text = event.author?.ifBlank { itemView.context.getString(R.string.user_fallback) }
                ?: itemView.context.getString(R.string.user_fallback)
            binding.date.text = itemView.context.getString(
                R.string.event_date_line,
                event.type,
                formatServerDate(event.datetime),
            )
            binding.content.text = event.content
            binding.likes.text = itemView.context.getString(R.string.likes_count, event.likeOwnerIds.size)
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Event, newItem: Event) = oldItem == newItem
    }
}
