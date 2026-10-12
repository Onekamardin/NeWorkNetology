package ru.netology.nework.ui.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.netology.nework.BuildConfig
import ru.netology.nework.R
import ru.netology.nework.domain.model.User
import ru.netology.nework.databinding.ItemUserBinding
import java.net.URL

class UserAdapter(private val onClick: (User) -> Unit = {}) : ListAdapter<User, UserAdapter.UserHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = UserHolder(
        ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: UserHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
        holder.itemView.setOnClickListener { onClick(user) }
    }

    override fun onViewRecycled(holder: UserHolder) {
        holder.cancelAvatarRequest()
        super.onViewRecycled(holder)
    }

    class UserHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        private var avatarRequest: Job? = null
        private var displayedAvatar: String? = null

        fun bind(user: User) {
            val login = user.login.orEmpty()
            binding.name.text = user.name?.takeIf(String::isNotBlank) ?: login
            binding.login.text = login
            showAvatar(user.avatar)
        }

        fun cancelAvatarRequest() {
            stopAvatarRequest()
            displayedAvatar = null
        }

        private fun stopAvatarRequest() {
            avatarRequest?.cancel()
            avatarRequest = null
        }

        private fun showAvatar(path: String?) {
            if (displayedAvatar == path) return
            stopAvatarRequest()
            displayedAvatar = path
            binding.avatar.setImageResource(android.R.drawable.sym_def_app_icon)
            if (path.isNullOrBlank()) return

            avatarRequest = CoroutineScope(Dispatchers.Main).launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        val baseUrl = BuildConfig.BASE_URL.removeSuffix("api/")
                        val imageUrl = if (path.startsWith("http://") || path.startsWith("https://")) {
                            path
                        } else {
                            baseUrl + path.removePrefix("/")
                        }
                        val connection = URL(imageUrl).openConnection().apply {
                            connectTimeout = 5_000
                            readTimeout = 5_000
                            setRequestProperty("Api-Key", BuildConfig.API_KEY)
                        }
                        connection.getInputStream().use(BitmapFactory::decodeStream)
                    }.getOrNull()
                }
                if (displayedAvatar == path && bitmap != null) {
                    binding.avatar.setImageBitmap(bitmap)
                }
            }
        }
    }

    private companion object Diff : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
