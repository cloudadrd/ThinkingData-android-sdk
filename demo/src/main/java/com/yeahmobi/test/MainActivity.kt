package com.yeahmobi.test

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button_track).setOnClickListener {
            Track.track()
        }

        findViewById<Button>(R.id.button_set_super).setOnClickListener {
            // 设置静态公共属性
            Track.setSuperProperties()
        }

        findViewById<Button>(R.id.button_delete_super).setOnClickListener {
            // 删除静态公共属性：channel
            Track.delSuperProperties("channel")
        }

        findViewById<Button>(R.id.button_clean_super).setOnClickListener {
            Track.cleanSuperProperties()
        }

        findViewById<Button>(R.id.button_get_super).setOnClickListener {
            Track.getSuperProperties()
        }

        findViewById<Button>(R.id.button_dynamic_super).setOnClickListener {
            Track.setDynamicSuperProperties()
        }

        findViewById<Button>(R.id.button_flush).setOnClickListener {
            Track.flush()
        }
    }
}