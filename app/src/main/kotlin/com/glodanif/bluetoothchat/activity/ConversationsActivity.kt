package com.glodanif.bluetoothchat.activity

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.ImageView
import com.amulyakhare.textdrawable.TextDrawable
import com.glodanif.bluetoothchat.R
import com.glodanif.bluetoothchat.adapter.ConversationsAdapter
import com.glodanif.bluetoothchat.entity.Conversation
import com.glodanif.bluetoothchat.model.*
import com.glodanif.bluetoothchat.presenter.ConversationsPresenter
import com.glodanif.bluetoothchat.view.ConversationsView
import com.glodanif.bluetoothchat.widget.ActionView


class ConversationsActivity : AppCompatActivity(), ConversationsView {

    private val REQUEST_SCAN = 101

    private lateinit var presenter: ConversationsPresenter
    private lateinit var settings: SettingsManager
    private val connection: BluetoothConnector = BluetoothConnectorImpl(this)
    private val storage: ConversationsStorage = ConversationsStorageImpl(this)

    private lateinit var conversationsList: RecyclerView
    private lateinit var noConversations: View
    private lateinit var addButton: FloatingActionButton
    private lateinit var actions: ActionView
    private lateinit var userAvatar: ImageView

    private val adapter: ConversationsAdapter = ConversationsAdapter()

    private var isStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversations)
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        settings = SettingsManagerImpl(this)
        presenter = ConversationsPresenter(this, connection, storage, settings)

        actions = findViewById(R.id.av_actions) as ActionView

        userAvatar = findViewById(R.id.iv_avatar) as ImageView
        conversationsList = findViewById(R.id.rv_conversations) as RecyclerView
        noConversations = findViewById(R.id.ll_empty_holder)
        conversationsList.layoutManager = LinearLayoutManager(this)
        conversationsList.adapter = adapter
        adapter.clickListener = { ChatActivity.start(this, it.deviceName, it.deviceAddress) }
        adapter.longClickListener = { showContextMenu(it) }

        addButton = findViewById(R.id.fab_new_conversation) as FloatingActionButton
        addButton.setOnClickListener {
            ScanActivity.startForResult(this, REQUEST_SCAN)
        }
        findViewById(R.id.btn_scan).setOnClickListener {
            ScanActivity.startForResult(this, REQUEST_SCAN)
        }
        userAvatar.setOnClickListener {
            ProfileActivity.start(this, true)
        }
    }

    private fun showContextMenu(conversation: Conversation) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Conversation options")
                .setItems(arrayOf("Remove"), { _, which ->
                    when (which) {
                        0 -> {
                            confirmRemoval(conversation)
                        }
                    }
                })
        builder.create().show()
    }

    private fun confirmRemoval(conversation: Conversation) {

        AlertDialog.Builder(this)
                .setMessage("Do you really want to remove this conversation? All messages will be lost.")
                .setPositiveButton("Yes", { _, _ -> presenter.removeConversation(conversation) })
                .setNegativeButton("No", null)
                .show()
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        presenter.prepareConnection()
        presenter.loadUserProfile()
    }

    override fun onStop() {
        super.onStop()
        isStarted = false
        presenter.releaseConnection()
    }

    override fun hideActions() {
        actions.visibility = View.GONE
    }

    override fun showNoConversations() {
        conversationsList.visibility = View.GONE
        addButton.visibility = View.GONE
        noConversations.visibility = View.VISIBLE
    }

    override fun showConversations(conversations: List<Conversation>, connected: String?) {
        conversationsList.visibility = View.VISIBLE
        addButton.visibility = View.VISIBLE
        noConversations.visibility = View.GONE

        adapter.setData(ArrayList(conversations), connected)
        adapter.notifyDataSetChanged()
    }

    override fun showServiceDestroyed() {

        if (!isStarted) return

        AlertDialog.Builder(this)
                .setMessage("Bluetooth Chat service just has been stopped, restart the service to be able to use the app")
                .setPositiveButton("Restart", { _, _ ->
                    presenter.prepareConnection()
                    presenter.loadUserProfile()
                })
                .setCancelable(false)
                .show()
    }

    override fun refreshList(connected: String?) {
        adapter.setCurrentConversation(connected)
        adapter.notifyDataSetChanged()
    }

    override fun notifyAboutConnectedDevice(conversation: Conversation) {

        actions.visibility = View.VISIBLE
        actions.setActions("${conversation.displayName} (${conversation.deviceName}) has just connected to you",
                ActionView.Action("Start chat") { presenter.startChat(conversation) },
                ActionView.Action("Disconnect") { presenter.rejectConnection() }
        )
    }

    override fun showRejectedNotification(conversation: Conversation) {

        if (!isStarted) return

        AlertDialog.Builder(this)
                .setMessage("Your connection request to ${conversation.displayName} (${conversation.deviceName}) was rejected")
                .setPositiveButton(getString(R.string.general__ok), null)
                .setCancelable(false)
                .show()
    }

    override fun redirectToChat(conversation: Conversation) {
        ChatActivity.start(this, conversation.deviceName, conversation.deviceAddress)
    }

    override fun showUserProfile(name: String, color: Int) {
        val symbol = if (name.isEmpty()) "?" else name[0].toString().toUpperCase()
        val drawable = TextDrawable.builder().buildRound(symbol, color)
        userAvatar.setImageDrawable(drawable)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SCAN && resultCode == Activity.RESULT_OK) {
            val device = data
                    ?.getParcelableExtra<BluetoothDevice>(ScanActivity.EXTRA_BLUETOOTH_DEVICE)

            if (device != null) {
                ChatActivity.start(this, device)
            }
        }
    }

    companion object {

        fun start(context: Context) =
                context.startActivity(Intent(context, ConversationsActivity::class.java))
    }
}
