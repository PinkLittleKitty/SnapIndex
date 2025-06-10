package com.santyfisela.snapIndex

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.accompanist.pager.*
import com.santyfisela.snapIndex.ui.theme.SnapIndexTheme
import kotlinx.coroutines.launch
import android.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.BitmapFactory
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.*
import kotlin.math.sqrt


class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnapIndexTheme(darkTheme = true, dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SnapIndexApp()
                }
            }
        }
    }
}

@Composable
fun SnapIndexApp() {
    val context = LocalContext.current

    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }

    val embeddingCache = remember { mutableStateOf<MutableMap<String, FloatArray>>(mutableMapOf()) }

    // Load embedding cache once
    LaunchedEffect(Unit) {
        val loadedCache = EmbeddingCacheManager.loadCache(context)
        embeddingCache.value = loadedCache

        // Load last folder URI on app start
        loadLastFolderUri(context)?.let { lastUri ->
            selectedFolderUri = lastUri
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            selectedFolderUri = uri
            saveLastFolderUri(context, uri)  // Save on new folder selection
        }
    }

    LaunchedEffect(selectedFolderUri) {
        selectedFolderUri?.let { folderUri ->
            isLoading = true
            progress = 0
            total = 0
            photos = emptyList()

            val loadedPhotos = loadImagesWithEmbeddings(
                context = context,
                folderUri = folderUri,
                embeddingCache = embeddingCache,
                onProgressUpdate = { processed, totalCount ->
                    progress = processed
                    total = totalCount
                }
            )

            photos = loadedPhotos
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading images... ($progress/$total)")
            }
        }
    } else {
        SnapIndexScreen(
            photos = photos,
            onFolderClick = { imagePicker.launch(null) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SnapIndexScreen(
    photos: List<Photo>,
    onFolderClick: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var sortNewest by remember { mutableStateOf(true) }
    var showPager by remember { mutableStateOf(false) }
    var startIndex by remember { mutableStateOf(0) }

    val displayed = remember(photos, search, sortNewest) {
        val sorted = if (sortNewest) photos.sortedByDescending { it.date } else photos.sortedBy { it.date }

        if (search.isBlank()) {
            sorted
        } else {
            val query = search.trim()
            sorted.filter { photo ->
                val searchableText = buildString {
                    append(photo.name)
                    append(" ")
                    append(photo.metadata.values.joinToString(" "))
                }
                searchableText.contains(query, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SnapIndex") },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { sortNewest = !sortNewest }) {
                        Icon(
                            imageVector = if (sortNewest) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Sort"
                        )
                    }
                    IconButton(onClick = onFolderClick) {
                        Icon(Icons.Default.Folder, contentDescription = "Select Folder")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            AnimatedVisibility(showSearch) {
                val keyboardController = LocalSoftwareKeyboardController.current

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                        }
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayed.size) { i ->
                    AsyncImage(
                        model = displayed[i].uri,
                        contentDescription = displayed[i].name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable {
                                startIndex = i
                                showPager = true
                            }
                    )
                }
            }
        }
    }

    if (showPager) {
        FullscreenPager(
            images = displayed.map { it.uri },
            start = startIndex,
            onDismiss = { showPager = false }
        )
    }
}

fun loadImagesFromDevice(context: Context): MutableList<Photo> {
    val photos = mutableListOf<Photo>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_TAKEN
    )

    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val name = cursor.getString(nameCol)
            val size = cursor.getLong(sizeCol)
            val date = cursor.getLong(dateCol)

            val metadata = getImageDetails(context, uri.toString())
            photos.add(Photo(uri.toString(), name, size, date, metadata))
        }
    }
    return photos
}

fun loadImagesFromFolder(context: Context, folderUri: Uri): MutableList<Photo> {
    val photos = mutableListOf<Photo>()
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        folderUri, DocumentsContract.getTreeDocumentId(folderUri)
    )

    context.contentResolver.query(
        childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        while (cursor.moveToNext()) {
            val docId = cursor.getString(idCol)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)

            if (docUri.toString().endsWith(".jpg", true) || docUri.toString().endsWith(".jpeg", true) || docUri.toString().endsWith(".png", true)) {
                val metadata = getImageDetails(context, docUri.toString())
                photos.add(Photo(docUri.toString(), docUri.lastPathSegment ?: "Unknown", 0L, 0L, metadata))
            }
        }
    }
    return photos
}

suspend fun loadImagesWithEmbeddings(
    context: Context,
    folderUri: Uri,
    embeddingCache: MutableState<MutableMap<String, FloatArray>>,
    onProgressUpdate: (processed: Int, total: Int) -> Unit
): List<Photo> = withContext(Dispatchers.IO) {
    val photos = mutableListOf<Photo>()
    val embedder = ImageEmbedder(context)

    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        folderUri, DocumentsContract.getTreeDocumentId(folderUri)
    )

    val imageUris = mutableListOf<Uri>()
    context.contentResolver.query(
        childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        while (cursor.moveToNext()) {
            val docId = cursor.getString(idCol)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
            if (docUri.toString().endsWith(".jpg", true) ||
                docUri.toString().endsWith(".jpeg", true) ||
                docUri.toString().endsWith(".png", true)) {
                imageUris.add(docUri)
            }
        }
    }

    onProgressUpdate(0, imageUris.size)

    imageUris.forEachIndexed { index, uri ->
        try {
            val key = uri.toString()
            val bitmap = loadBitmapFromUri(context, key)
            val embedding = embeddingCache.value[key] ?: bitmap?.let {
                embedder.extractEmbedding(it).also { result ->
                    // Update cache map (mutable)
                    embeddingCache.value[key] = result
                }
            }

            if (embedding != null) {
                photos.add(Photo(key, uri.lastPathSegment ?: "Unknown", 0L, 0L, emptyMap(), embedding))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onProgressUpdate(index + 1, imageUris.size)
    }

    // Save updated cache after loading
    EmbeddingCacheManager.saveCache(context, embeddingCache.value)

    photos
}

fun findNearestNeighbors(
    queryEmbedding: FloatArray,
    photos: List<Photo>,
    maxResults: Int = 10
): List<Photo> {
    return photos
        .mapNotNull { photo ->
            val dist = photo.embedding?.let { cosineSimilarity(queryEmbedding, it) }
            dist?.let { Pair(photo, it) }
        }
        .sortedByDescending { it.second }  // cosine similarity higher = closer
        .take(maxResults)
        .map { it.first }
}

// Cosine similarity between two vectors
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dot / ((sqrt(normA) * sqrt(normB)) + 1e-10f)
}

fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@OptIn(ExperimentalPagerApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullscreenPager(
    images: List<String>,
    start: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = start)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = bottomSheetState
        ) {
            val details = getImageDetails(context, images[pagerState.currentPage])

            Column(Modifier.padding(16.dp)) {
                Text("Image Details", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))

                details.forEach { (key, value) ->
                    Text("$key: $value")
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(images[pagerState.currentPage]))
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { showSheet = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        HorizontalPager(
            count = images.size,
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) { page ->
            ZoomableImage(
                imageUrl = images[page],
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
fun ZoomableImage(imageUrl: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += offsetChange.x
        offsetY += offsetChange.y
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { if (scale > 1f) scale = 1f else scale = 2f },
                    onTap = { onDismiss() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .transformable(state)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

fun getImageDetails(context: Context, uri: String): Map<String, String> {
    val details = mutableMapOf<String, String>()
    val resolver = context.contentResolver
    val parsedUri = Uri.parse(uri)

    val cursor = resolver.query(parsedUri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(MediaStore.Images.Media.SIZE)
            val dateIndex = it.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)

            val name = if (nameIndex != -1) it.getString(nameIndex) else "Unknown"
            val size = if (sizeIndex != -1) it.getLong(sizeIndex) else 0L
            val date = if (dateIndex != -1) it.getLong(dateIndex) else 0L

            details["File Name"] = name
            details["File Size"] = formatFileSize(size)
            details["Date Taken"] = if (date > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(date)) else "Unknown"
        }
    }

    try {
        resolver.openInputStream(parsedUri)?.use { inputStream ->
            val exif = ExifInterface(inputStream)

            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            details["Resolution"] = if (width > 0 && height > 0) "${width}x$height" else "Unknown"

            val latLongArray = FloatArray(2)
            val hasLatLong = exif.getLatLong(latLongArray)
            if (hasLatLong) {
                details["Location"] = "Lat: ${latLongArray[0]}, Lon: ${latLongArray[1]}"
            } else {
                details["Location"] = "Unavailable"
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return details
}

fun saveLastFolderUri(context: Context, uri: Uri) {
    val prefs = context.getSharedPreferences("snapindex_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("last_folder_uri", uri.toString()).apply()
}

fun loadLastFolderUri(context: Context): Uri? {
    val prefs = context.getSharedPreferences("snapindex_prefs", Context.MODE_PRIVATE)
    return prefs.getString("last_folder_uri", null)?.let { Uri.parse(it) }
}

@Composable
fun ImageGallery(photos: List<Photo>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(photos) { photo ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    AsyncImage(
                        model = Uri.parse(photo.uri),
                        contentDescription = photo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                    Text(
                        text = photo.name,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

fun formatFileSize(sizeInBytes: Long): String {
    val kb = 1024
    val mb = kb * 1024

    return when {
        sizeInBytes >= mb -> String.format("%.2f MB", sizeInBytes.toDouble() / mb)
        sizeInBytes >= kb -> String.format("%.2f KB", sizeInBytes.toDouble() / kb)
        else -> "$sizeInBytes bytes"
    }
}