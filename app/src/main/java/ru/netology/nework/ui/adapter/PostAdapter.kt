package ru.netology.nework.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nework.data.model.Post
import ru.netology.nework.databinding.ItemPostBinding
import ru.netology.nework.R
import ru.netology.nework.ui.formatServerDate

class PostAdapter : ListAdapter<Post, PostAdapter.PostHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PostHolder(
        ItemPostBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: PostHolder, position: Int) = holder.bind(getItem(position))

    class PostHolder(private val binding: ItemPostBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: Post) {
            binding.author.text = post.author?.ifBlank { itemView.context.getString(R.string.user_fallback) }
                ?: itemView.context.getString(R.string.user_fallback)
            binding.date.text = formatServerDate(post.published)
            binding.content.text = post.content
            binding.likes.text = itemView.context.getString(R.string.likes_count, post.likeOwnerIds.size)
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Post, newItem: Post) = oldItem == newItem
    }
}
