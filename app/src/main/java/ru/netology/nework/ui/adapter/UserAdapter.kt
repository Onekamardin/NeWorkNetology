package ru.netology.nework.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nework.data.model.User
import ru.netology.nework.databinding.ItemUserBinding

class UserAdapter : ListAdapter<User, UserAdapter.UserHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = UserHolder(
        ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: UserHolder, position: Int) = holder.bind(getItem(position))

    class UserHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.name.text = user.name.ifBlank { user.login }
            binding.login.text = user.login
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
