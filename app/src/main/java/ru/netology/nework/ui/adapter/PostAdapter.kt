package ru.netology.nework.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.PopupMenu
import ru.netology.nework.domain.model.Post
import ru.netology.nework.databinding.ItemPostBinding
import ru.netology.nework.R
import ru.netology.nework.ui.formatServerDate
import ru.netology.nework.ui.openLink

class PostAdapter(
    private val onLike: (Post) -> Unit,
    private val onOpen: (Post) -> Unit,
    private val currentUserId: () -> Long?,
    private val onDelete: (Post) -> Unit,
) : ListAdapter<Post, PostAdapter.PostHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PostHolder(
        ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: PostHolder, position: Int) {
        val post = getItem(position)
        holder.bind(post)
        holder.likeButton.setOnClickListener { onLike(post) }
        holder.itemView.setOnClickListener { onOpen(post) }
        val canDelete = currentUserId()?.let { it == post.authorId } == true
        holder.binding.menu.visibility = if (canDelete) View.VISIBLE else View.GONE
        holder.binding.menu.setOnClickListener { button ->
            PopupMenu(button.context, button).apply {
                menu.add(R.string.delete).setOnMenuItemClickListener {
                    onDelete(post)
                    true
                }
                show()
            }
        }
    }

    class PostHolder(internal val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            binding.author.text = post.author?.ifBlank { itemView.context.getString(R.string.user_fallback) }
                ?: itemView.context.getString(R.string.user_fallback)
            binding.date.text = formatServerDate(post.published)
            binding.content.text = post.content
            binding.link.text = post.link
            binding.link.visibility = if (post.link.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
            binding.link.setOnClickListener { post.link?.let { openLink(itemView.context, it) } }
            val textId = if (post.likedByMe) R.string.likes_count_liked else R.string.likes_count
            binding.likes.text = itemView.context.getString(textId, post.likeOwnerIds.size)
        }

        val likeButton get() = binding.likes
    }

    private companion object Diff : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Post, newItem: Post) = oldItem == newItem
    }
}
