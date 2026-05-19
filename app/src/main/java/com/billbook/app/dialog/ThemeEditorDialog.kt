package com.billbook.app.dialog

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.billbook.app.R
import com.billbook.app.util.ThemeManager

class ThemeEditorDialog : DialogFragment() {

    private var onApply: ((String) -> Unit)? = null
    private var selectedImageUri: Uri? = null
    private lateinit var themeManager: ThemeManager

    private lateinit var layoutImagePreview: FrameLayout
    private lateinit var ivPreview: ImageView
    private lateinit var btnClearImage: FrameLayout

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                showImagePreview(uri)
            }
        }
    }

    fun setOnApplyListener(listener: (String) -> Unit) {
        onApply = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_theme_editor, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        themeManager = ThemeManager(requireContext())
        selectedImageUri = themeManager.getCustomDioramaImageUri()

        layoutImagePreview = view.findViewById(R.id.layoutImagePreview)
        ivPreview = view.findViewById(R.id.ivPreview)
        btnClearImage = view.findViewById(R.id.btnClearImage)
        val btnPickImage = view.findViewById<FrameLayout>(R.id.btnPickImage)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancel)
        val btnApply = view.findViewById<TextView>(R.id.btnApply)

        // Show existing image if any
        if (selectedImageUri != null) {
            showImagePreview(selectedImageUri!!)
        }

        btnPickImage.setOnClickListener { openImagePicker() }

        btnClearImage.setOnClickListener {
            selectedImageUri = null
            layoutImagePreview.visibility = View.GONE
            btnClearImage.visibility = View.GONE
        }

        btnCancel.setOnClickListener { dismiss() }

        btnApply.setOnClickListener {
            if (selectedImageUri != null) {
                themeManager.setCustomDioramaImage(selectedImageUri!!)
            } else {
                themeManager.clearCustomDioramaImage()
            }
            onApply?.invoke(themeManager.getCurrentPaletteKey())
            dismiss()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        imagePickerLauncher.launch(intent)
    }

    private fun showImagePreview(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                ivPreview.setImageBitmap(bitmap)
                layoutImagePreview.visibility = View.VISIBLE
                btnClearImage.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "图片加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAG = "ThemeEditorDialog"
    }
}
