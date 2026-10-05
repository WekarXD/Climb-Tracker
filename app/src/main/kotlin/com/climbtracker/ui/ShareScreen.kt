package com.climbtracker.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Full-screen preview of the picture about to be shared, with the choices that change it.
 * [preview] is null while it is being drawn; [ownPhoto] says the wall photo has been replaced.
 */
@Composable
fun ShareScreen(
    title: String,
    preview: Bitmap?,
    centred: Boolean,
    ownPhoto: Boolean,
    onCentred: (Boolean) -> Unit,
    onPickPhoto: (Uri) -> Unit,
    onWallPhoto: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPickPhoto(uri) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Dark status bar icons: the dialog's window would otherwise draw them white on the pale screen.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = Cream, contentColor = Ink) {
            Column(Modifier.systemBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("Cerrar", color = Terracotta, style = MaterialTheme.typography.titleMedium) }
                    Text(
                        title,
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(24.dp)).background(Ink), contentAlignment = Alignment.Center) {
                        if (preview == null) {
                            Spinner(color = Color.White)
                        } else {
                            Image(preview.asImageBitmap(), contentDescription = "Imagen que se va a compartir", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = CardWhite, border = BorderStroke(1.dp, Hairline)) {
                    Row(Modifier.padding(4.dp)) {
                        Choice(Icons.AutoMirrored.Filled.FormatAlignLeft, "Texto a la izquierda", !centred) { onCentred(false) }
                        Choice(Icons.Default.FormatAlignCenter, "Texto centrado", centred) { onCentred(true) }
                    }
                }
                Row(Modifier.padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (ownPhoto) onWallPhoto() else picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Hairline),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = CardWhite, contentColor = Ink),
                    ) {
                        Icon(if (ownPhoto) Icons.Default.Wallpaper else Icons.Default.PhotoLibrary, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (ownPhoto) "Ver la pared" else "Elegir foto", maxLines = 1)
                    }
                    Button(
                        onClick = onShare,
                        enabled = preview != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Color.White),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Compartir", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 56.dp, height = 44.dp).clip(CircleShape).background(if (selected) Terracotta else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (selected) Color.White else Ink)
    }
}
