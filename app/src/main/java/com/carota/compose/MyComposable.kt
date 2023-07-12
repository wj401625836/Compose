package com.carota.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/*
 * Copyright (C) 2018-2020 CAROTA Technology Crop. <www.carota.ai>.
 * All Rights Reserved.
 *
 * Unauthorized using, copying, distributing and modifying of this file,
 * via any medium is strictly prohibited.
 *
 * Proprietary and confidential.
*/

var globalText by mutableStateOf("")

@Composable
fun MyComposable() {
    val scrollState = rememberScrollState()
    Text(
        text = globalText,
        color = Color.White,
        modifier = Modifier
            .padding(bottom = 50.dp, start = 10.dp, end = 10.dp)
            .background(Color.Black)
            .verticalScroll(scrollState)
    )
}

fun updateGlobalText(newText: String) {
    globalText = newText
    println("wangjian updateGlobalText = $globalText")
}