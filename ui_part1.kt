    }
    
    var flashEnabled by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var currentZoom by remember { mutableStateOf(1f) }
    val animatedZoom by animateFloatAsState(
        targetValue = currentZoom,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
    )
    LaunchedEffect(animatedZoom) {
        cameraController.setZoomRatio(animatedZoom)
    }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    
    LaunchedEffect(Unit) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                lastCapturedUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
    }
    
    var isFrontCamera by remember { mutableStateOf(false) }
    val cameraRotation by animateFloatAsState(
        targetValue = if (isFrontCamera) 180f else 0f, 
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
    )
    
    var videoResolutions by remember { mutableStateOf(listOf("720p", "1080p")) }
    var supportedFps by remember { mutableStateOf(listOf("30")) }
    var currentVideoRes by remember { mutableStateOf("1080p") }
    var currentFps by remember { mutableStateOf("30") }
    
    var hasProRAW by remember { mutableStateOf(false) }
    var hasHighRes by remember { mutableStateOf(false) }
    var hasManualAperture by remember { mutableStateOf(false) }

    var isRawEnabled by remember { mutableStateOf(false) }
    var isHighResEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(isFrontCamera) {
        try {
            val cameraManager = context.getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
            val cameraIdList = cameraManager.cameraIdList
            val targetLens = if (isFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val cameraId = cameraIdList.firstOrNull { 
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == targetLens 
            } ?: cameraIdList.firstOrNull()

            if (cameraId != null) {
                val chars = cameraManager.getCameraCharacteristics(cameraId)
                val streamConfig = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                
                val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                hasProRAW = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
                
                val sizes = streamConfig?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                val maxRes = sizes?.maxByOrNull { it.width * it.height }
                hasHighRes = (maxRes != null && (maxRes.width * maxRes.height) > 20_000_000)

                val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                hasManualAperture = apertures != null && apertures.size > 1

                val videoSizes = streamConfig?.getOutputSizes(android.media.MediaRecorder::class.java)
                val res = mutableListOf<String>()
                if (videoSizes?.any { it.height == 720 || it.width == 1280 } == true) res.add("720p")
                if (videoSizes?.any { it.height == 1080 || it.width == 1920 } == true) res.add("1080p")
                if (videoSizes?.any { it.height >= 2160 || it.width >= 3840 } == true) res.add("4K")
                
                if (res.isEmpty()) {
                    res.addAll(listOf("720p", "1080p"))
                }
                videoResolutions = res
                if (!res.contains(currentVideoRes)) currentVideoRes = res.last()
                
                val fpsList = mutableListOf("30")
                val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                if (fpsRanges?.any { it.upper >= 60 } == true) {
                    fpsList.add("60")
                }
                supportedFps = fpsList
                if (!fpsList.contains(currentFps)) currentFps = fpsList.first()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            videoResolutions = listOf("720p", "1080p")
            supportedFps = listOf("30")
            if (!videoResolutions.contains(currentVideoRes)) currentVideoRes = "1080p"
            if (!supportedFps.contains(currentFps)) currentFps = "30"
        }
    }
    
    var isBursting by remember { mutableStateOf(false) }
    var burstJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    DisposableEffect(cameraController, lifecycleOwner) {
        onDispose { }
    }

    val aspectRatios = listOf("FULL", "9:16", "3:4", "1:1")
    var currentAspect by remember { mutableStateOf("FULL") }

    var currentTimer by remember { mutableStateOf(0) }
    var activeTimer by remember { mutableStateOf(0) }
    var timerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    var isFadeEffectEnabled by remember { mutableStateOf(false) }
    var isBouncyAnimationEnabled by remember { mutableStateOf(false) }
    var isAiCleaning by remember { mutableStateOf(false) }
    var uiColor by remember { mutableStateOf(Color(0xFFFFC107)) }
    
    var showSwipeDropdown by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showTimerDropdown by remember { mutableStateOf(false) }
    var showResOptions by remember { mutableStateOf(false) }
    var showFpsOptions by remember { mutableStateOf(false) }
    var isNightModeAuto by remember { mutableStateOf(false) }
    var isWatermarkEnabled by remember { mutableStateOf(false) }
    
    val toneGen = remember { android.media.ToneGenerator(AudioManager.STREAM_SYSTEM, 100) }
    
    var showBlink by remember { mutableStateOf(false) }

    val takePhoto = {
        scope.launch {
            showBlink = true
            delay(50)
            showBlink = false
        }
        
        sound.play(MediaActionSound.SHUTTER_CLICK)
        
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
            }
        }
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
        
        cameraController.takePicture(
            outputOptions, 
            ContextCompat.getMainExecutor(context), 
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let { uri ->
                        if (isAiCleaning) {
                            scope.launch {
                                delay(1500)
                                lastCapturedUri = uri
                                isAiCleaning = false
                            }
                        } else {
                            lastCapturedUri = uri
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    isAiCleaning = false
                }
            }
        )
        if (isFadeEffectEnabled) {
             isAiCleaning = true
        }
    }

    val startVideoRecording = {
        try {
            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraApp")
                }
            }
            val outputOptions = androidx.camera.video.MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()
            sound.play(MediaActionSound.START_VIDEO_RECORDING)
            recording = cameraController.startRecording(
                outputOptions,
                AudioConfig.create(true),
                ContextCompat.getMainExecutor(context)
            ) { event ->
                if (event is VideoRecordEvent.Start) isRecording = true
                else if (event is VideoRecordEvent.Finalize) {
                    isRecording = false
                    event.outputResults.outputUri.let { uri -> lastCapturedUri = uri }
                    if (event.hasError()) event.cause?.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Viewfinder
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val fullAspect = maxWidth.value / maxHeight.value
            val targetAspect = when (currentAspect) {
                "1:1" -> 1f
                "3:4" -> 3f/4f
                "9:16" -> 9f/16f
                else -> fullAspect
            }
            val animatedAspect by animateFloatAsState(
                targetValue = targetAspect,
                animationSpec = spring(stiffness = Spring.StiffnessLow)
            )
            
            Box(modifier = Modifier.aspectRatio(animatedAspect)) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            controller = cameraController
                            cameraController.bindToLifecycle(lifecycleOwner)
                        }
                    },
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (pan.y > 40f) {
                                showSettingsScreen = true
                                showTimerDropdown = false
                            } else if (pan.y < -40f) {
                                showSettingsScreen = false
                            }
                            val zoomDelta = if (zoom > 1f) 1f + (zoom - 1f) * 1.5f else 1f - (1f - zoom) * 1.5f
                            currentZoom = (currentZoom * zoomDelta).coerceIn(0.6f, 10f)
                            cameraController.setZoomRatio(currentZoom)
                        }
                    }.graphicsLayer {
                        rotationX = cameraRotation
                    }
                )
                
                AnimatedVisibility(
                    visible = showBlink,
                    enter = fadeIn(animationSpec = tween(50)),
                    exit = fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.8f)))
                }
                
                if (isWatermarkEnabled) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                        Text(
                            text = "AI Camera",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            
        }
        
        // Gradient Fade
        if (isFadeEffectEnabled) {
