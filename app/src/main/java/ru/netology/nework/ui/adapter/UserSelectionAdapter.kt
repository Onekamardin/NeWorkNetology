package ru.netology.nework.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.netology.nework.databinding.ItemUserSelectionBinding
import ru.netology.nework.domain.model.User
import ru.netology.nework.ui.utils.loadServerImage

class UserSelectionAdapter(
    private val isSelected: (Long) -> Boolean,
    private val onSelected: (Long, Boolean) -> Unit,
) : ListAdapter<User, UserSelectionAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemUserSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class Holder(private val binding: ItemUserSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.userName.text = user.name?.takeIf(String::isNotBlank) ?: user.login.orEmpty()
            binding.userLogin.text = user.login.orEmpty()
            binding.userAvatar.loadServerImage(user.avatar)
            binding.userSelected.setOnCheckedChangeListener(null)
            binding.userSelected.isChecked = isSelected(user.id)
            binding.userSelected.setOnCheckedChangeListener { _, checked -> onSelected(user.id, checked) }
            binding.root.setOnClickListener { binding.userSelected.toggle() }
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}

