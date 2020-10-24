package com.naseem.aamir.linq

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar


class ShareActivity : AppCompatActivity() {
    private var tags = mutableListOf<EditText>()
    private var links = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setTitle(R.string.share_open_links)
            actionBar.setHomeButtonEnabled(true)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
        val str = intent.getStringExtra("data")
        if(str.isNullOrEmpty()) {
            Toast.makeText(this, "No data", Toast.LENGTH_SHORT).show()
            finish()
            return
        } else {
            val arr = str.split(";")
            val parent = findViewById<LinearLayout>(R.id.root_container)
            arr.forEach {
                val view = layoutInflater.inflate(R.layout.share_item, parent, false)
                val l = view.findViewById<EditText>(R.id.link)
                val t = view.findViewById<EditText>(R.id.link_tag)
                links.add(l)
                tags.add(t)
                l.setText(it)
                view.findViewById<View>(R.id.go_to_link).setOnClickListener { _->
                    gotoURL(it)
                }
                parent.addView(view, 0)
            }
        }
        val share = findViewById<View>(R.id.share_button)
        share.setOnClickListener {
            var text = ""
            tags.forEachIndexed { index, editText ->
                text = "${tags[index].text}\n${links[index].text}\n\n$text"
            }
            if (text.isEmpty().not()) shareText(text.trim('\n'))
        }
    }

    private fun shareText(text: String) {
        val share = Intent(Intent.ACTION_SEND)
        share.type = "text/plain"
        share.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "Share"));
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return false
    }

    private fun gotoURL(url: String) {
        val u: String
        if (url.startsWith("www")) u = "http://$url"
        else u = url
        if(url.startsWith("https://") || url.startsWith("http://")) {
            val uri: Uri = Uri.parse(u)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Invalid url", Toast.LENGTH_SHORT).show()
        }
    }
}