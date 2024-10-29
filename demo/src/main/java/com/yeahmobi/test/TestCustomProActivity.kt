package com.yeahmobi.test

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TestCustomProActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_pro)

        val etEventName = findViewById<EditText>(R.id.et_event_name_value)
        val etEventPro = findViewById<EditText>(R.id.et_event_pro_value)

        findViewById<View>(R.id.btn_track).setOnClickListener {
            val eventName = etEventName.text.toString()
            if (TextUtils.isEmpty(eventName)) {
                Toast.makeText(this, "事件名不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var eventPro = etEventPro.text.toString()
            if (TextUtils.isEmpty(eventPro)) {
                eventPro = "{}";
            }
            Track.testTrack(eventName, eventPro)
        }

    }


}