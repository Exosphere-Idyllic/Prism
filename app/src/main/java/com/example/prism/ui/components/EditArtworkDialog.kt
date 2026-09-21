package com.example.prism.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.prism.R
import com.example.prism.ui.theme.*

/**
 * Dialog for changing or resetting the custom artwork/cover of a song or album.
 * Supports:
 * 1. Selecting an image from the local gallery via Android Photo Picker (PickVisualMedia).
 * 2. Entering an image URL (HTTP/HTTPS) with live Coil preview.
 * 3. Resetting back to default artwork.
 */
@Composable
fun EditArtworkDialog(
    title: String,
    currentCustomUri: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf(currentCustomUri) }
    var urlInput by remember {
        mutableStateOf(
            if (currentCustomUri.startsWith("http://", ignoreCase = true) ||
                currentCustomUri.startsWith("https://", ignoreCase = true)
            ) {
                currentCustomUri
            } else {
                ""
            }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not support persistable permissions
            }
            selectedUri = uri.toString()
            urlInput = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.edit_artwork_title),
                color = AppTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    color = AppTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Preview Container
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppSurface2),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(selectedUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.preview),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = stringResource(R.string.preview),
                            tint = AppTextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Option 1: Pick from Gallery button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppSurface2)
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.choose_from_gallery),
                        tint = AppAccentSoft,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.choose_from_gallery),
                        color = AppTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Option 2: Enter URL section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.enter_image_url),
                        color = AppTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppSurface2)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (urlInput.isEmpty()) {
                            Text(
                                text = stringResource(R.string.image_url_hint),
                                color = AppTextSecondary.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = urlInput,
                            onValueChange = { input ->
                                urlInput = input
                                selectedUri = input.trim()
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = AppTextPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(AppAccentSoft),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Option 3: Reset to Default (if custom URI is present)
                if (currentCustomUri.isNotEmpty() || selectedUri.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selectedUri = ""
                            urlInput = ""
                        },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.reset_artwork),
                            tint = AppAccentSoft,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.reset_artwork),
                            color = AppAccentSoft,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(selectedUri.trim())
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.save),
                    color = AppAccentSoft,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = AppTextSecondary
                )
            }
        },
        containerColor = AppSurface
    )
}
