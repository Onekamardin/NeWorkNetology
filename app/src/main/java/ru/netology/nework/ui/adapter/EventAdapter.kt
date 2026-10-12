package ru.netology.nework.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.PopupMenu
import ru.netology.nework.domain.model.Event
import ru.netology.nework.databinding.ItemEventBinding
import ru.netology.nework.R
import ru.netology.nework.ui.formatServerDate
import ru.netology.nework.ui.openLink

class EventAdapter(
    private val onLike: (Event) -> Unit,
    private val onOpen: (Event) -> Unit,
    private val currentUserId: () -> Long?,
    private val onDelete: (Event) -> Unit,
) : ListAdapter<Event, EventAdapter.EventHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EventHolder(
        ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: EventHolder, position: Int) {
        val event = getItem(position)
        holder.bind(event)
        holder.likeButton.setOnClickListener { onLike(event) }
        holder.itemView.setOnClickListener { onOpen(event) }
        val canDelete = currentUserId()?.let { it == event.authorId } == true
        holder.binding.menu.visibility = if (canDelete) View.VISIBLE else View.GONE
        holder.binding.menu.setOnClickListener { button ->
            PopupMenu(button.context, button).apply {
                menu.add(R.string.delete).setOnMenuItemClickListener {
                    onDelete(event)
                    true
                }
                show()
            }
        }
    }

    class EventHolder(internal val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: Event) {
            binding.author.text = event.author?.ifBlank { itemView.context.getString(R.string.user_fallback) }
                ?: itemView.context.getString(R.string.user_fallback)
            binding.date.text = itemView.context.getString(
                R.string.event_date_line,
                event.type,
                formatServerDate(event.datetime),
            )
            binding.content.text = event.content
            binding.link.text = event.link
            binding.link.visibility = if (event.link.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.link.setOnClickListener { event.link?.let { openLink(itemView.context, it) } }
            val textId = if (event.likedByMe) R.string.likes_count_liked else R.string.likes_count
            binding.likes.text = itemView.context.getString(textId, event.likeOwnerIds.size)
        }

        val likeButton get() = binding.likes
    }

    private companion object Diff : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Event, newItem: Event) = oldItem == newItem
    }
}
