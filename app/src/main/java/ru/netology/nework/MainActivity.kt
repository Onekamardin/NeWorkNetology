package ru.netology.nework

import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.netology.nework.databinding.ActivityMainBinding
import ru.netology.nework.data.model.Event
import ru.netology.nework.data.model.Post
import ru.netology.nework.data.model.User
import ru.netology.nework.ui.FeedState
import ru.netology.nework.ui.MainViewModel
import ru.netology.nework.ui.adapter.EventAdapter
import ru.netology.nework.ui.adapter.PostAdapter
import ru.netology.nework.ui.adapter.UserAdapter

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private val postAdapter = PostAdapter()
    private val eventAdapter = EventAdapter()
    private val userAdapter = UserAdapter()
    private var selectedTab = R.id.menu_posts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(true)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.createButton.setOnClickListener {
            showMessage(getString(R.string.create_action_coming_soon))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.posts.collect { render(it, postAdapter, getString(R.string.no_posts)) } }
                launch { viewModel.events.collect { render(it, eventAdapter, getString(R.string.no_events)) } }
                launch { viewModel.users.collect { render(it, userAdapter, getString(R.string.no_users)) } }
            }
        }

        showTab(R.id.menu_posts)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_login, R.id.action_register -> {
                showMessage(getString(R.string.auth_screen_coming_soon))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showTab(itemId: Int) {
        selectedTab = itemId
        binding.messageText.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.adapter = when (itemId) {
            R.id.menu_events -> {
                binding.toolbar.title = getString(R.string.events)
                binding.createButton.visibility = View.VISIBLE
                eventAdapter
            }
            R.id.menu_users -> {
                binding.toolbar.title = getString(R.string.users)
                binding.createButton.visibility = View.GONE
                userAdapter
            }
            else -> {
                binding.toolbar.title = getString(R.string.posts)
                binding.createButton.visibility = View.VISIBLE
                postAdapter
            }
        }

        when (itemId) {
            R.id.menu_events -> viewModel.loadEvents()
            R.id.menu_users -> viewModel.loadUsers()
            else -> viewModel.loadPosts()
        }
    }

    private fun <T : Any> render(state: FeedState<T>, adapter: RecyclerView.Adapter<*>, emptyText: String) {
        if (selectedTab == R.id.menu_posts && adapter !is PostAdapter) return
        if (selectedTab == R.id.menu_events && adapter !is EventAdapter) return
        if (selectedTab == R.id.menu_users && adapter !is UserAdapter) return

        binding.recyclerView.adapter = adapter
        binding.progressBar.visibility = if (state.loading && state.items.isEmpty()) View.VISIBLE else View.GONE
        val text = state.error ?: if (state.items.isEmpty() && !state.loading) emptyText else null
        binding.messageText.text = text
        binding.messageText.visibility = if (text == null) View.GONE else View.VISIBLE

        when (adapter) {
            is PostAdapter -> adapter.submitList(state.items.filterIsInstance<Post>())
            is EventAdapter -> adapter.submitList(state.items.filterIsInstance<Event>())
            is UserAdapter -> adapter.submitList(state.items.filterIsInstance<User>())
        }
    }

    private fun showMessage(text: String) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
    }
}
