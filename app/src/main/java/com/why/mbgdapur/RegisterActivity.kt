package com.why.mbgdapur

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        findViewById<MaterialCardView>(R.id.cardSchool).setOnClickListener {
            val intent = Intent(this, RegisterSchoolActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialCardView>(R.id.cardVendor).setOnClickListener {
            val intent = Intent(this, RegisterVendorActivity::class.java)
            startActivity(intent)
        }

        findViewById<MaterialCardView>(R.id.cardDriver).setOnClickListener {
            val intent = Intent(this, RegisterDriverActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}
