package ru.netology.nework

import android.os.Bundle
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.netology.nework.databinding.ActivityMainBinding
import ru.netology.nework.databinding.ScreenLoginBinding
import ru.netology.nework.databinding.ScreenRegisterBinding
import ru.netology.nework.databinding.ScreenCreatePostBinding
import ru.netology.nework.data.model.Event
import ru.netology.nework.data.model.Post
import ru.netology.nework.data.model.User
import ru.netology.nework.ui.FeedState
import ru.netology.nework.ui.AuthOperation
import ru.netology.nework.ui.AuthState
import ru.netology.nework.ui.CreatePostState
import ru.netology.nework.ui.MainViewModel
import ru.netology.nework.ui.adapter.EventAdapter
import ru.netology.nework.ui.adapter.PostAdapter
import ru.netology.nework.ui.adapter.UserAdapter

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private var loginBinding: ScreenLoginBinding? = null
    private var registerBinding: ScreenRegisterBinding? = null
    private var createPostBinding: ScreenCreatePostBinding? = null
    private var selectedAvatarUri: Uri? = null
    private var authScreenOpen = false
    private var postCreationScreenOpen = false

    private val avatarPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) showSelectedAvatar(uri)
    }

    private val postAdapter = PostAdapter()
    private val eventAdapter = EventAdapter()
    private val userAdapter = UserAdapter()
    private var selectedTab = R.id.menu_posts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        binding.feedScreen.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.feedScreen.recyclerView.setHasFixedSize(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (authScreenOpen || postCreationScreenOpen) showFeedScreen() else finish()
            }
        })

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.createButton.setOnClickListener {
            if (selectedTab == R.id.menu_posts) showCreatePostScreen()
            else showMessage(getString(R.string.create_event_coming_soon))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.posts.collect { render(it, postAdapter, getString(R.string.no_posts)) } }
                launch { viewModel.events.collect { render(it, eventAdapter, getString(R.string.no_events)) } }
                launch { viewModel.users.collect { render(it, userAdapter, getString(R.string.no_users)) } }
                launch { viewModel.auth.collect(::renderAuthState) }
                launch { viewModel.createPost.collect(::renderCreatePostState) }
            }
        }

        showTab(R.id.menu_posts)
    }

    /** Оставляет содержимое экрана ниже статус-бара и выше системной навигации. */
    private fun applySystemBarInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val initialLeft = binding.root.paddingLeft
        val initialTop = binding.root.paddingTop
        val initialRight = binding.root.paddingRight
        val initialBottom = binding.root.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                initialLeft + safeInsets.left,
                initialTop + safeInsets.top,
                initialRight + safeInsets.right,
                initialBottom + safeInsets.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_bar_menu, menu)
        val authenticated = viewModel.isAuthenticated()
        menu.findItem(R.id.action_save).isVisible = postCreationScreenOpen
        menu.findItem(R.id.action_login).isVisible = !authenticated && !postCreationScreenOpen
        menu.findItem(R.id.action_register).isVisible = !authenticated && !postCreationScreenOpen
        menu.findItem(R.id.action_profile).isVisible = authenticated && !postCreationScreenOpen
        menu.findItem(R.id.action_logout).isVisible = authenticated && !postCreationScreenOpen
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_login -> {
                showLoginScreen()
                true
            }
            R.id.action_register -> {
                showRegisterScreen()
                true
            }
            R.id.action_profile -> {
                showMessage(getString(R.string.profile_coming_soon))
                true
            }
            R.id.action_save -> {
                savePost()
                true
            }
            R.id.action_logout -> {
                viewModel.logout()
                invalidateOptionsMenu()
                showMessage(getString(R.string.logout_success))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLoginScreen() {
        viewModel.clearAuthState()
        authScreenOpen = true
        postCreationScreenOpen = false
        createPostBinding = null
        registerBinding = null
        binding.screenContainer.removeAllViews()
        val screen = ScreenLoginBinding.inflate(layoutInflater, binding.screenContainer, false)
        loginBinding = screen
        binding.screenContainer.addView(screen.root)
        binding.bottomNavigation.visibility = View.GONE
        binding.createButton.visibility = View.GONE
        binding.toolbar.title = getString(R.string.login_title)

        screen.loginSubmit.setOnClickListener {
            val login = screen.loginInput.text?.toString()?.trim().orEmpty()
            val password = screen.passwordInput.text?.toString().orEmpty()
            screen.loginLayout.error = if (login.isBlank()) getString(R.string.error_required) else null
            screen.passwordLayout.error = if (password.isBlank()) getString(R.string.error_required) else null
            if (login.isNotBlank() && password.isNotBlank()) viewModel.login(login, password)
        }
        screen.openRegister.setOnClickListener { showRegisterScreen() }
    }

    private fun showRegisterScreen() {
        viewModel.clearAuthState()
        authScreenOpen = true
        postCreationScreenOpen = false
        createPostBinding = null
        loginBinding = null
        selectedAvatarUri = null
        binding.screenContainer.removeAllViews()
        val screen = ScreenRegisterBinding.inflate(layoutInflater, binding.screenContainer, false)
        registerBinding = screen
        binding.screenContainer.addView(screen.root)
        binding.bottomNavigation.visibility = View.GONE
        binding.createButton.visibility = View.GONE
        binding.toolbar.title = getString(R.string.register_title)

        screen.selectAvatar.setOnClickListener { avatarPicker.launch("image/*") }
        screen.registerSubmit.setOnClickListener {
            val login = screen.loginInput.text?.toString()?.trim().orEmpty()
            val name = screen.nameInput.text?.toString()?.trim().orEmpty()
            val password = screen.passwordInput.text?.toString().orEmpty()
            val repeatedPassword = screen.repeatPasswordInput.text?.toString().orEmpty()
            screen.loginLayout.error = if (login.isBlank()) getString(R.string.error_required) else null
            screen.nameLayout.error = if (name.isBlank()) getString(R.string.error_required) else null
            screen.passwordLayout.error = if (password.isBlank()) getString(R.string.error_required) else null
            screen.repeatPasswordLayout.error = when {
                repeatedPassword.isBlank() -> getString(R.string.error_required)
                repeatedPassword != password -> getString(R.string.error_password_mismatch)
                else -> null
            }
            val valid = login.isNotBlank() && name.isNotBlank() && password.isNotBlank() &&
                repeatedPassword.isNotBlank() && repeatedPassword == password
            if (valid) {
                val avatarPart = selectedAvatarUri?.let(::createAvatarPart)
                if (selectedAvatarUri != null && avatarPart == null) {
                    showMessage(getString(R.string.register_photo_read_error))
                } else {
                    viewModel.register(login, name, password, avatarPart)
                }
            }
        }
        screen.openLogin.setOnClickListener { showLoginScreen() }
    }

    private fun showSelectedAvatar(uri: Uri) {
        val mimeType = contentResolver.getType(uri)
        if (mimeType !in setOf("image/jpeg", "image/png")) {
            showMessage(getString(R.string.error_avatar_type))
            return
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val dimensions = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
                bounds.outWidth to bounds.outHeight
            }
        }.getOrNull()
        val width = dimensions?.first ?: 0
        val height = dimensions?.second ?: 0
        if (width <= 0 || height <= 0 || width > 2048 || height > 2048) {
            showMessage(getString(R.string.error_avatar_size))
            return
        }
        selectedAvatarUri = uri
        registerBinding?.avatarPreview?.setImageURI(uri)
    }

    private fun createAvatarPart(uri: Uri): MultipartBody.Part? = runCatching {
        val mimeType = contentResolver.getType(uri) ?: return@runCatching null
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            else -> return@runCatching null
        }
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        val body = bytes.toRequestBody(mimeType.toMediaType())
        MultipartBody.Part.createFormData("file", "avatar.$extension", body)
    }.getOrNull()

    private fun renderAuthState(state: AuthState) {
        when (state) {
            AuthState.Idle -> Unit
            is AuthState.Loading -> setAuthLoading(state.operation, true)
            is AuthState.Success -> {
                setAuthLoading(state.operation, false)
                showFeedScreen()
                invalidateOptionsMenu()
                showMessage(
                    getString(if (state.operation == AuthOperation.LOGIN) R.string.login_success else R.string.register_success),
                )
                viewModel.clearAuthState()
            }
            is AuthState.HttpError -> {
                setAuthLoading(state.operation, false)
                val message = when {
                    state.code == 400 && state.operation == AuthOperation.LOGIN -> getString(R.string.login_bad_credentials)
                    (state.code == 400 || state.code == 403) && state.operation == AuthOperation.REGISTER -> getString(R.string.register_duplicate_login)
                    state.code == 415 && state.operation == AuthOperation.REGISTER -> getString(R.string.register_photo_format_error)
                    else -> getString(R.string.server_error_code, state.code)
                }
                showMessage(message)
                viewModel.clearAuthState()
            }
            is AuthState.NetworkError -> {
                setAuthLoading(state.operation, false)
                val title = if (state.operation == AuthOperation.REGISTER) {
                    R.string.register_title
                } else {
                    R.string.login_title
                }
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(getString(R.string.auth_network_error_details, state.detail))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                viewModel.clearAuthState()
            }
        }
    }

    private fun setAuthLoading(operation: AuthOperation, loading: Boolean) {
        if (operation == AuthOperation.LOGIN) {
            loginBinding?.loginProgress?.visibility = if (loading) View.VISIBLE else View.GONE
            loginBinding?.loginSubmit?.isEnabled = !loading
        } else {
            registerBinding?.registerProgress?.visibility = if (loading) View.VISIBLE else View.GONE
            registerBinding?.registerSubmit?.isEnabled = !loading
        }
    }

    private fun showFeedScreen() {
        authScreenOpen = false
        postCreationScreenOpen = false
        loginBinding = null
        registerBinding = null
        createPostBinding = null
        binding.screenContainer.removeAllViews()
        binding.screenContainer.addView(binding.feedScreen.root)
        binding.bottomNavigation.visibility = View.VISIBLE
        binding.toolbar.title = getString(R.string.app_name)
        invalidateOptionsMenu()
        showTab(selectedTab)
    }

    private fun showCreatePostScreen() {
        if (!viewModel.isAuthenticated()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.auth_required_to_create)
                .setPositiveButton(R.string.login) { _, _ -> showLoginScreen() }
                .setNeutralButton(R.string.register) { _, _ -> showRegisterScreen() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        authScreenOpen = false
        postCreationScreenOpen = true
        loginBinding = null
        registerBinding = null
        binding.screenContainer.removeAllViews()
        val screen = ScreenCreatePostBinding.inflate(layoutInflater, binding.screenContainer, false)
        createPostBinding = screen
        binding.screenContainer.addView(screen.root)
        binding.bottomNavigation.visibility = View.GONE
        binding.createButton.visibility = View.GONE
        binding.toolbar.title = getString(R.string.create_post_title)
        invalidateOptionsMenu()
        screen.cancelButton.setOnClickListener { showFeedScreen() }
    }

    private fun savePost() {
        val screen = createPostBinding ?: return
        val content = screen.contentInput.text?.toString()?.trim().orEmpty()
        screen.contentLayout.error = if (content.isBlank()) getString(R.string.error_required) else null
        if (content.isNotBlank()) viewModel.createPost(content)
    }

    private fun renderCreatePostState(state: CreatePostState) {
        val screen = createPostBinding ?: return
        when (state) {
            CreatePostState.Idle -> Unit
            CreatePostState.Loading -> {
                screen.createProgress.visibility = View.VISIBLE
                screen.cancelButton.isEnabled = false
                binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = false
            }
            CreatePostState.Success -> {
                binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = true
                showMessage(getString(R.string.post_created))
                viewModel.clearCreatePostState()
                showFeedScreen()
            }
            is CreatePostState.Error -> {
                screen.createProgress.visibility = View.GONE
                screen.cancelButton.isEnabled = true
                binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = true
                showMessage(state.message)
                viewModel.clearCreatePostState()
            }
        }
    }

    private fun showTab(itemId: Int) {
        selectedTab = itemId
        binding.feedScreen.messageText.visibility = View.GONE
        binding.feedScreen.progressBar.visibility = View.GONE
        binding.feedScreen.recyclerView.adapter = when (itemId) {
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

        binding.feedScreen.recyclerView.adapter = adapter
        binding.feedScreen.progressBar.visibility = if (state.loading && state.items.isEmpty()) View.VISIBLE else View.GONE
        val text = state.error ?: if (state.items.isEmpty() && !state.loading) emptyText else null
        binding.feedScreen.messageText.text = text
        binding.feedScreen.messageText.visibility = if (text == null) View.GONE else View.VISIBLE

        when (adapter) {
            is PostAdapter -> adapter.submitList(state.items.filterIsInstance<Post>())
            is EventAdapter -> adapter.submitList(state.items.filterIsInstance<Event>())
            is UserAdapter -> adapter.submitList(state.items.filterIsInstance<User>())
        }
    }

    private fun showMessage(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
